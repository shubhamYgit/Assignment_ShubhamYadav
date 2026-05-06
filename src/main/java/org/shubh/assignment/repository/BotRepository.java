package org.shubh.assignment.repository;

import org.shubh.assignment.domain.Bot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BotRepository extends JpaRepository<Bot, Long> {
    boolean existsByName(String name);
}
