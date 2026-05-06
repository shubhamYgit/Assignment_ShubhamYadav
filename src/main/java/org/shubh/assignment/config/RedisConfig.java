package org.shubh.assignment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisConfig {
    @Bean
    public DefaultRedisScript<Long> reserveBotReplyScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/reserve_bot_reply.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
