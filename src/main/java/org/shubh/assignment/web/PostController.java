package org.shubh.assignment.web;

import jakarta.validation.Valid;
import org.shubh.assignment.api.AddCommentRequest;
import org.shubh.assignment.api.CommentResponse;
import org.shubh.assignment.api.CreatePostRequest;
import org.shubh.assignment.api.LikePostRequest;
import org.shubh.assignment.api.LikePostResponse;
import org.shubh.assignment.api.PostResponse;
import org.shubh.assignment.service.AssignmentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
public class PostController {
    private final AssignmentService service;

    public PostController(AssignmentService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse createPost(@Valid @RequestBody CreatePostRequest request) {
        return PostResponse.from(service.createPost(request));
    }

    @PostMapping("/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse addComment(@PathVariable long postId, @Valid @RequestBody AddCommentRequest request) {
        return service.addComment(postId, request);
    }

    @PostMapping("/{postId}/like")
    public LikePostResponse likePost(@PathVariable long postId, @Valid @RequestBody LikePostRequest request) {
        return service.likePost(postId, request.userId());
    }
}
