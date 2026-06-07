package com.wkr.aicode.config;

import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisChatMemoryStoreConfig {

    @Bean
    public RedisChatMemoryStore redisChatMemoryStore(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port,
            @Value("${spring.data.redis.user:default}") String user,
            @Value("${spring.data.redis.password}") String password,
            @Value("${spring.data.redis.ttl:3600}") long ttl) {
        return RedisChatMemoryStore.builder()
                .host(host)
                .port(port)
                .user(user)
                .password(password)
                .ttl(ttl)
                .build();
    }
}
