package com.newsquery.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;
import java.util.Arrays;

/**
 * Phase 4: Redis + Caffeine 2단계 캐싱 설정
 *
 * L1 Cache (Caffeine): 로컬 메모리, 빠르지만 크기 제한 (100MB)
 * L2 Cache (Redis): 분산 캐시, 공유 가능, 용량 크지만 느림 (1GB)
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory();
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .disableCachingNullValues();

        // NQL 쿼리 캐시 (1시간)
        RedisCacheConfiguration nqlConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .disableCachingNullValues();

        // 벡터 임베딩 캐시 (24시간)
        RedisCacheConfiguration embeddingConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(24))
                .disableCachingNullValues();

        // GROUP BY 결과 캐시 (5분)
        RedisCacheConfiguration groupByConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("nql_queries", nqlConfig)
                .withCacheConfiguration("embeddings", embeddingConfig)
                .withCacheConfiguration("groupby_results", groupByConfig)
                .build();
    }

    /**
     * Caffeine L1 로컬 캐시 (핫 데이터용)
     */
    @Bean
    public com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineCache() {
        return Caffeine.newBuilder()
                .maximumSize(10000)  // 최대 10,000 항목
                .expireAfterWrite(Duration.ofMinutes(5))
                .recordStats()
                .build();
    }
}
