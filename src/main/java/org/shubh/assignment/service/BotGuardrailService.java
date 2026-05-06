package org.shubh.assignment.service;

import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class BotGuardrailService {
    private static final int BOT_REPLY_LIMIT = 100;
    private static final int COOLDOWN_SECONDS = 600;
    private static final int NOOP_COOLDOWN_SECONDS = 1;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> reserveBotReplyScript;

    public BotGuardrailService(StringRedisTemplate redisTemplate, DefaultRedisScript<Long> reserveBotReplyScript) {
        this.redisTemplate = redisTemplate;
        this.reserveBotReplyScript = reserveBotReplyScript;
    }

    public BotReservation reserveBotReply(long postId, long botId, Long humanId) {
        boolean skipCooldown = humanId == null;
        String cooldownKey = skipCooldown ? noopCooldownKey(postId, botId) : cooldownKey(botId, humanId);
        int cooldownSeconds = skipCooldown ? NOOP_COOLDOWN_SECONDS : COOLDOWN_SECONDS;
        Long result = redisTemplate.execute(
                reserveBotReplyScript,
                List.of(botCountKey(postId), cooldownKey),
                String.valueOf(BOT_REPLY_LIMIT),
                String.valueOf(cooldownSeconds),
                skipCooldown ? "1" : "0");

        if (result == null) {
            throw new GuardrailRejectedException("Redis guardrail reservation failed");
        }
        if (result == -1) {
            throw new GuardrailRejectedException("Bot cooldown active for human " + humanId);
        }
        if (result == -2) {
            throw new GuardrailRejectedException("Horizontal cap exceeded for post " + postId);
        }
        return new BotReservation(postId, skipCooldown ? null : cooldownKey);
    }

    public void release(BotReservation reservation) {
        redisTemplate.opsForValue().decrement(botCountKey(reservation.postId()));
        if (reservation.cooldownKey() != null) {
            redisTemplate.delete(reservation.cooldownKey());
        }
    }

    private String botCountKey(long postId) {
        return "post:" + postId + ":bot_count";
    }

    private String cooldownKey(long botId, long humanId) {
        return "cooldown:bot_" + botId + ":human_" + humanId;
    }

    private String noopCooldownKey(long postId, long botId) {
        return "cooldown:noop:post_" + postId + ":bot_" + botId;
    }

    public record BotReservation(long postId, String cooldownKey) {
    }
}
