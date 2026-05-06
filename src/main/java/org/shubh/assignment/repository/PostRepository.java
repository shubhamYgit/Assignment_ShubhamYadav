package org.shubh.assignment.repository;

import org.shubh.assignment.domain.Post;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {
}
