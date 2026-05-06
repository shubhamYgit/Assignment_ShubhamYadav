package org.shubh.assignment.api;

import java.time.Instant;
import org.shubh.assignment.domain.AuthorType;
import org.shubh.assignment.domain.Post;

public record PostResponse(
        Long id,
        Long authorId,
        AuthorType authorType,
        String content,
        Instant createdAt
) {
    public static PostResponse from(Post post) {
        return new PostResponse(post.getId(), post.getAuthorId(), post.getAuthorType(), post.getContent(), post.getCreatedAt());
    }
}
