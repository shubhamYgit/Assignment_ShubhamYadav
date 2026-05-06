package org.shubh.assignment.service;

import org.shubh.assignment.domain.Bot;
import org.shubh.assignment.domain.User;
import org.shubh.assignment.repository.BotRepository;
import org.shubh.assignment.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SeedData {
    @Bean
    CommandLineRunner seedUsersAndBots(UserRepository users, BotRepository bots) {
        return args -> {
            createUserIfMissing(users, "alice", false);
            createUserIfMissing(users, "bob", true);

            for (int i = 1; i <= 200; i++) {
                String name = "Bot " + i;
                if (!bots.existsByName(name)) {
                    bots.save(new Bot(name, "Discussion bot " + i));
                }
            }
        };
    }

    private void createUserIfMissing(UserRepository users, String username, boolean premium) {
        if (!users.existsByUsername(username)) {
            users.save(new User(username, premium));
        }
    }
}
