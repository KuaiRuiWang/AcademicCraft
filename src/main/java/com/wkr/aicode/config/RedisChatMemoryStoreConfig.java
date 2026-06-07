package com.wkr.aicode.config;

import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "spring.data.redis")
@Data
public class RedisChatMemoryStoreConfig {

    private String host;

    private int port;

    private int database;

    private String password;

    private long ttl;

    @Bean
    public RedisChatMemoryStoreWithDb redisChatMemoryStoreDb() {
        RedisChatMemoryStoreWithDb redisChatMemoryStoreWithDb = RedisChatMemoryStoreWithDb.builder()
                .host(host)
                .port(port)
                .password(password)
                .user("default")
                .database(database)
                .ttl(ttl)
                .build();
        return redisChatMemoryStoreWithDb;
    }
}

