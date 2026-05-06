package org.shubh.assignment.service;

import org.shubh.assignment.api.AddCommentRequest;
import org.shubh.assignment.api.CommentResponse;
import org.shubh.assignment.api.CreatePostRequest;
import org.shubh.assignment.api.LikePostResponse;
import org.shubh.assignment.domain.AuthorType;
import org.shubh.assignment.domain.Bot;
import org.shubh.assignment.domain.Comment;
import org.shubh.assignment.domain.Post;
import org.shubh.assignment.repository.BotRepository;
import org.shubh.assignment.repository.CommentRepository;
import org.shubh.assignment.repository.PostRepository;
import org.shubh.assignment.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignmentService {
    private static final int MAX_DEPTH = 20;

    private final UserRepository users;
    private final BotRepository bots;
    private final PostRepository posts;
    private final CommentRepository comments;
    private final ViralityService viralityService;
    private final BotGuardrailService botGuardrails;
    private final NotificationService notificationService;

    public AssignmentService(
            UserRepository users,
            BotRepository bots,
            PostRepository posts,
            CommentRepository comments,
            ViralityService viralityService,
            BotGuardrailService botGuardrails,
            NotificationService notificationService) {
        this.users = users;
        this.bots = bots;
        this.posts = posts;
        this.comments = comments;
        this.viralityService = viralityService;
        this.botGuardrails = botGuardrails;
        this.notificationService = notificationService;
    }

    @Transactional
    public Post createPost(CreatePostRequest request) {
        requireAuthor(request.authorType(), request.authorId());
        return posts.save(new Post(request.authorId(), request.authorType(), request.content()));
    }

    @Transactional
    public CommentResponse addComment(long postId, AddCommentRequest request) {
        Post post = posts.findById(postId)
                .orElseThrow(() -> new NotFoundException("Post " + postId + " not found"));
        requireAuthor(request.authorType(), request.authorId());

        Comment parent = null;
        int depth = 1;
        if (request.parentCommentId() != null) {
            parent = comments.findById(request.parentCommentId())
                    .orElseThrow(() -> new NotFoundException("Parent comment " + request.parentCommentId() + " not found"));
            if (!parent.getPost().getId().equals(postId)) {
                throw new GuardrailRejectedException("Parent comment does not belong to post " + postId);
            }
            depth = parent.getDepthLevel() + 1;
        }

        if (depth > MAX_DEPTH) {
            throw new GuardrailRejectedException("Vertical cap exceeded. depth_level cannot exceed " + MAX_DEPTH);
        }

        if (request.authorType() == AuthorType.BOT) {
            return addBotComment(post, parent, request, depth);
        }

        Comment saved = comments.save(new Comment(post, parent, request.authorId(), request.authorType(), request.content(), depth));
        viralityService.incrementScore(postId, InteractionType.HUMAN_COMMENT);
        return toCommentResponse(saved, postId, request.parentCommentId());
    }

    @Transactional
    public LikePostResponse likePost(long postId, long userId) {
        if (!posts.existsById(postId)) {
            throw new NotFoundException("Post " + postId + " not found");
        }
        if (!users.existsById(userId)) {
            throw new NotFoundException("User " + userId + " not found");
        }
        long score = viralityService.incrementScore(postId, InteractionType.HUMAN_LIKE);
        return new LikePostResponse(postId, userId, score);
    }

    private CommentResponse addBotComment(Post post, Comment parent, AddCommentRequest request, int depth) {
        Long targetHumanId = targetHumanId(post, parent);
        BotGuardrailService.BotReservation reservation =
                botGuardrails.reserveBotReply(post.getId(), request.authorId(), targetHumanId);
        boolean viralityIncremented = false;
        try {
            Comment saved = comments.saveAndFlush(new Comment(post, parent, request.authorId(), request.authorType(), request.content(), depth));
            viralityService.incrementScore(post.getId(), InteractionType.BOT_REPLY);
            viralityIncremented = true;
            notifyHumanIfNeeded(targetHumanId, request.authorId(), post.getId());
            Long parentId = parent == null ? null : parent.getId();
            return toCommentResponse(saved, post.getId(), parentId);
        } catch (RuntimeException ex) {
            if (viralityIncremented) {
                viralityService.decrementScore(post.getId(), InteractionType.BOT_REPLY);
            }
            botGuardrails.release(reservation);
            throw ex;
        }
    }

    private CommentResponse toCommentResponse(Comment comment, long postId, Long parentCommentId) {
        return CommentResponse.of(
                comment.getId(),
                postId,
                parentCommentId,
                comment.getAuthorId(),
                comment.getAuthorType(),
                comment.getContent(),
                comment.getDepthLevel(),
                comment.getCreatedAt());
    }

    private void notifyHumanIfNeeded(Long targetHumanId, long botId, long postId) {
        if (targetHumanId == null) {
            return;
        }
        Bot bot = bots.findById(botId)
                .orElseThrow(() -> new NotFoundException("Bot " + botId + " not found"));
        notificationService.handleBotInteraction(
                targetHumanId,
                bot.getName() + " replied to your post " + postId);
    }

    private Long targetHumanId(Post post, Comment parent) {
        if (parent != null && parent.getAuthorType() == AuthorType.USER) {
            return parent.getAuthorId();
        }
        if (post.getAuthorType() == AuthorType.USER) {
            return post.getAuthorId();
        }
        return null;
    }

    private void requireAuthor(AuthorType type, long authorId) {
        boolean exists = type == AuthorType.USER ? users.existsById(authorId) : bots.existsById(authorId);
        if (!exists) {
            throw new NotFoundException(type + " author " + authorId + " not found");
        }
    }
}
