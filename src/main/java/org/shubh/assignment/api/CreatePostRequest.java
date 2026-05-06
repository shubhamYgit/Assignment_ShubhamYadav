package org.shubh.assignment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.shubh.assignment.domain.AuthorType;

public record CreatePostRequest(
        @NotNull Long authorId,
        @NotNull AuthorType authorType,
        @NotBlank String content
) {
}
