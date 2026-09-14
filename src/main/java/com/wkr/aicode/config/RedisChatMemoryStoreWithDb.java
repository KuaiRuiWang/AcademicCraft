package com.wkr.aicode.config;

import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStoreException;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.internal.ValidationUtils;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.Connection;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;

import java.util.ArrayList;
import java.util.List;

/**
 * 根据 dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore.class 源码修改而来
 * 新增database的自定义
 */
// 自定义支持 Database 配置的 RedisChatMemoryStore
public class RedisChatMemoryStoreWithDb implements ChatMemoryStore {

    /** 建连超时：经 frp 转发时握手往返明显长于本机，但不应无限等待 */
    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    /** 读写超时：会话记忆单次存取是小报文，10s 足够，超时即快速失败而非挂死 */
    private static final int SOCKET_TIMEOUT_MILLIS = 10_000;
    /** 驱逐线程扫描间隔 */
    private static final long EVICT_RUN_INTERVAL_MILLIS = 30_000L;
    /** 连接空闲超过该时长后可被驱逐（配合 testWhileIdle 探活） */
    private static final long MIN_EVICTABLE_IDLE_MILLIS = 60_000L;
    private static final int DEFAULT_MAX_TOTAL = 8;

    private final JedisPooled client;
    private final String keyPrefix;
    private final Long ttl;
    // 新增 database 参数的构造方法
    public RedisChatMemoryStoreWithDb(String host, Integer port, String user, String password, int database, String prefix, Long ttl) {
        // 校验必填参数
        String finalHost = ValidationUtils.ensureNotBlank(host, "host");
        int finalPort = ValidationUtils.ensureNotNull(port, "port");
        int finalDb = ValidationUtils.ensureNotNull(database, "database"); // 确保 Database 是正整数
        this.keyPrefix = ValidationUtils.ensureNotNull(prefix, "prefix");
        this.ttl = ValidationUtils.ensureNotNull(ttl, "ttl");

        // 1. 构建 JedisClientConfig（包含 Database 配置）
        DefaultJedisClientConfig.Builder clientConfigBuilder = DefaultJedisClientConfig.builder()
                .database(finalDb) // 关键：指定 Redis Database 索引
                // Redis 可能位于 frp 公网转发之后，必须显式设置超时，
                // 否则隧道半死时连接会无限挂起而不是快速失败
                .connectionTimeoutMillis(CONNECT_TIMEOUT_MILLIS)
                .socketTimeoutMillis(SOCKET_TIMEOUT_MILLIS);

        // 2. 配置用户名密码（若有）
        if (user != null && !user.trim().isEmpty()) {
            String finalUser = ValidationUtils.ensureNotBlank(user, "user");
            String finalPassword = ValidationUtils.ensureNotBlank(password, "password");
            clientConfigBuilder.user(finalUser).password(finalPassword);
        }

        // 3. 实例化 JedisPooled（带 Database 配置与连接探活）
        //
        // 注意：这里必须用 GenericObjectPoolConfig<Connection>，不能用 JedisPoolConfig。
        // JedisPoolConfig 继承的是 GenericObjectPoolConfig<Jedis>，而 Java 泛型不协变，
        // 无法匹配 JedisPooled(HostAndPort, JedisClientConfig, GenericObjectPoolConfig<Connection>)。
        //
        // 连接探活是必须的：全栈工作流单个 Agent 调模型耗时可达 2 分钟以上，
        // 这段时间池中连接完全空闲；若 Redis 经由 frp/NAT 转发，空闲连接会被静默掐断。
        // 默认配置（testOnBorrow=false、无驱逐线程）会把已死的连接原样借出，
        // 首次写入即抛 JedisConnectionException: Connection reset by peer。
        GenericObjectPoolConfig<Connection> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setTestOnBorrow(true);                          // 借出前 PING 探活，兜住驱逐间隙
        poolConfig.setTestWhileIdle(true);                         // 空闲连接在驱逐时也做探活
        poolConfig.setTimeBetweenEvictionRunsMillis(EVICT_RUN_INTERVAL_MILLIS);
        poolConfig.setMinEvictableIdleTimeMillis(MIN_EVICTABLE_IDLE_MILLIS);
        poolConfig.setMaxTotal(DEFAULT_MAX_TOTAL);
        poolConfig.setMaxIdle(DEFAULT_MAX_TOTAL);

        HostAndPort hostAndPort = new HostAndPort(finalHost, finalPort);
        this.client = new JedisPooled(hostAndPort, clientConfigBuilder.build(), poolConfig);
    }

    // ------------------------------ 复用原 RedisChatMemoryStore 的核心逻辑 ------------------------------
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String json = this.client.get(this.toRedisKey(memoryId));
        return json == null ? new ArrayList<>() : ChatMessageDeserializer.messagesFromJson(json);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String json = ChatMessageSerializer.messagesToJson(ValidationUtils.ensureNotEmpty(messages, "messages"));
        String key = this.toRedisKey(memoryId);
        String res;

        if (this.ttl > 0L) {
            res = this.client.setex(key, this.ttl, json);
        } else {
            res = this.client.set(key, json);
        }

        if (!"OK".equals(res)) {
            throw new RedisChatMemoryStoreException("Set memory error, msg=" + res);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        this.client.del(this.toRedisKey(memoryId));
    }

    private String toMemoryIdString(Object memoryId) {
        boolean isNullOrEmpty = memoryId == null || memoryId.toString().trim().isEmpty();
        if (isNullOrEmpty) {
            throw new IllegalArgumentException("memoryId cannot be null or empty");
        }
        return memoryId.toString();
    }

    private String toRedisKey(Object memoryId) {
        return this.keyPrefix + this.toMemoryIdString(memoryId);
    }

    // ------------------------------ 提供 Builder 便于配置 ------------------------------
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String host;
        private Integer port;
        private String user;
        private String password;
        private int database = 0; // 默认 Database 0
        private Long ttl = 0L;
        private String prefix = "";

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(Integer port) {
            this.port = port;
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        // 新增 Database 配置方法
        public Builder database(int database) {
            this.database = database;
            return this;
        }

        public Builder ttl(Long ttl) {
            this.ttl = ttl;
            return this;
        }

        public Builder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public RedisChatMemoryStoreWithDb build() {
            return new RedisChatMemoryStoreWithDb(
                    this.host, this.port, this.user, this.password,
                    this.database, this.prefix, this.ttl
            );
        }
    }
}
