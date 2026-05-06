package org.shubh.assignment.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private static final Duration NOTIFICATION_COOLDOWN = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;

    public NotificationService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void handleBotInteraction(long userId, String message) {
        String cooldownKey = notificationCooldownKey(userId);
        Boolean firstNotification = redisTemplate.opsForValue()
                .setIfAbsent(cooldownKey, "1", NOTIFICATION_COOLDOWN);

        if (Boolean.TRUE.equals(firstNotification)) {
            System.out.println("Push Notification Sent to User " + userId + ": " + message);
            return;
        }

        redisTemplate.opsForList().rightPush(pendingListKey(userId), message);
    }

    @Scheduled(
            fixedRateString = "${assignment.notifications.sweep-rate-ms:300000}",
            initialDelayString = "${assignment.notifications.initial-delay-ms:300000}")
    public void sweepPendingNotifications() {
        ScanOptions options = ScanOptions.scanOptions().match("user:*:pending_notifs").count(100).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                flushPendingList(cursor.next());
            }
        } catch (RedisConnectionFailureException ex) {
            System.err.println("Redis unavailable during notification sweep: " + ex.getMessage());
        }
    }

    private void flushPendingList(String key) {
        List<String> messages = new ArrayList<>();
        String message;
        while ((message = redisTemplate.opsForList().leftPop(key)) != null) {
            messages.add(message);
        }

        if (messages.isEmpty()) {
            redisTemplate.delete(key);
            return;
        }

        String firstActor = extractActor(messages.get(0));
        if (messages.size() == 1) {
            System.out.println("Summarized Push Notification: " + firstActor + " interacted with your posts.");
        } else {
            System.out.println("Summarized Push Notification: " + firstActor + " and "
                    + (messages.size() - 1) + " others interacted with your posts.");
        }

        redisTemplate.delete(key);
    }

    private String pendingListKey(long userId) {
        return "user:" + userId + ":pending_notifs";
    }

    private String notificationCooldownKey(long userId) {
        return "user:" + userId + ":notif_cooldown";
    }

    private Long extractUserId(String key) {
        String[] parts = key.split(":");
        return parts.length >= 2 ? Long.parseLong(parts[1]) : null;
    }

    private String extractActor(String message) {
        int repliedIndex = message.indexOf(" replied");
        if (repliedIndex > 0) {
            return message.substring(0, repliedIndex);
        }
        int spaceIndex = message.indexOf(' ');
        return spaceIndex > 0 ? message.substring(0, spaceIndex) : message;
    }
}
