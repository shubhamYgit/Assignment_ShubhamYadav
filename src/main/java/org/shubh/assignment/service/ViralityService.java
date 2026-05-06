package org.shubh.assignment.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ViralityService {
    private final StringRedisTemplate redisTemplate;

    public ViralityService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public long incrementScore(long postId, InteractionType type) {
        Long score = redisTemplate.opsForValue().increment(viralityScoreKey(postId), type.scoreDelta());
        return score == null ? 0 : score;
    }

    public void decrementScore(long postId, InteractionType type) {
        redisTemplate.opsForValue().decrement(viralityScoreKey(postId), type.scoreDelta());
    }

    public long currentScore(long postId) {
        String value = redisTemplate.opsForValue().get(viralityScoreKey(postId));
        return value == null ? 0 : Long.parseLong(value);
    }

    private String viralityScoreKey(long postId) {
        return "post:" + postId + ":virality_score";
    }
}
