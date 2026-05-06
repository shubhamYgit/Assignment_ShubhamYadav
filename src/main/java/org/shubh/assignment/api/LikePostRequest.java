package org.shubh.assignment.api;

import jakarta.validation.constraints.NotNull;

public record LikePostRequest(@NotNull Long userId) {
}
