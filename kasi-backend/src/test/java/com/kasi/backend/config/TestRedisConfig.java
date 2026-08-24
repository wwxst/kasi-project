package com.kasi.backend.config;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * 测试环境嵌入式Redis配置
 * <p>
 * 启动嵌入式Redis服务器，避免测试依赖外部Redis实例。
 */
@TestConfiguration
public class TestRedisConfig {

    private RedisServer redisServer;
    private int redisPort;

    @Bean
    @Primary
    public RedisConnectionFactory testRedisConnectionFactory() throws IOException {
        if (redisServer == null) {
            redisPort = findAvailablePort();
            redisServer = RedisServer.builder()
                    .port(redisPort)
                    .setting("maxheap 128mb")
                    .setting("maxmemory 64mb")
                    .setting("save \"\"")
                    .build();
            redisServer.start();
        }

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("localhost", redisPort);
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        return factory;
    }

    @PreDestroy
    public void stopRedis() {
        if (redisServer != null) {
            redisServer.stop();
        }
    }

    private int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
