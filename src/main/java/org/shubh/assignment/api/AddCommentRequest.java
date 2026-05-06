package org.shubh.assignment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.shubh.assignment.domain.AuthorType;

public record AddCommentRequest(
        @NotNull Long authorId,
        @NotNull AuthorType authorType,
        Long parentCommentId,
        @NotBlank String content
) {
}
