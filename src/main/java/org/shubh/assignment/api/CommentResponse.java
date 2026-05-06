package org.shubh.assignment.api;

import java.time.Instant;
import org.shubh.assignment.domain.AuthorType;
import org.shubh.assignment.domain.Comment;

public record CommentResponse(
        Long id,
        Long postId,
        Long parentCommentId,
        Long authorId,
        AuthorType authorType,
        String content,
        int depthLevel,
        Instant createdAt
) {
    public static CommentResponse of(
            Long id,
            Long postId,
            Long parentCommentId,
            Long authorId,
            AuthorType authorType,
            String content,
            int depthLevel,
            Instant createdAt) {
        return new CommentResponse(id, postId, parentCommentId, authorId, authorType, content, depthLevel, createdAt);
    }

    public static CommentResponse from(Comment comment) {
        Long parentId = comment.getParentComment() == null ? null : comment.getParentComment().getId();
        return new CommentResponse(
                comment.getId(),
                comment.getPost().getId(),
                parentId,
                comment.getAuthorId(),
                comment.getAuthorType(),
                comment.getContent(),
                comment.getDepthLevel(),
                comment.getCreatedAt());
    }
}
