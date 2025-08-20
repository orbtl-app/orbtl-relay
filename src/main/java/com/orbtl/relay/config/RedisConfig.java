package com.orbtl.relay.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

@Configuration
public class RedisConfig {
    
    @Bean
    public ReactiveRedisTemplate<String, byte[]> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        
        RedisSerializationContext<String, byte[]> serializationContext = 
            RedisSerializationContext.<String, byte[]>newSerializationContext()
                .key(RedisSerializer.string())
                .value(RedisSerializer.byteArray())
                .hashKey(RedisSerializer.string())
                .hashValue(RedisSerializer.byteArray())
                .build();
        
        return new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
    }
    
    @Bean
    public ReactiveRedisMessageListenerContainer reactiveRedisMessageListenerContainer(
            ReactiveRedisConnectionFactory connectionFactory) {
        
        return new ReactiveRedisMessageListenerContainer(connectionFactory);
    }
}