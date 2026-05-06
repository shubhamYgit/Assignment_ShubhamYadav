package org.shubh.assignment.repository;

import org.shubh.assignment.domain.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    long countByPostId(Long postId);
}
