package com.robustvision.platform.config;

import com.robustvision.platform.service.LiveUpdateService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import java.nio.charset.StandardCharsets;

@Configuration
@ConditionalOnProperty(name="app.queue.mode",havingValue="redis")
public class LiveUpdateRedisConfig {
    @Bean RedisMessageListenerContainer liveRedisListener(RedisConnectionFactory factory, LiveUpdateService live) {
        var container=new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener((message,pattern)->live.receive(new String(message.getBody(),StandardCharsets.UTF_8)),new ChannelTopic(LiveUpdateService.CHANNEL));
        return container;
    }
}
