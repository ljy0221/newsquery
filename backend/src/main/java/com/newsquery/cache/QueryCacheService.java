package com.newsquery.cache;

import com.newsquery.api.NewsSearchResponse;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Phase 4: NQL 쿼리 캐싱 서비스
 * - Redis L2 캐시를 통한 분산 캐싱
 * - 캐시 키: NQL 쿼리 정규화 후 SHA-256 해시
 */
@Service
public class QueryCacheService {

    /**
     * NQL 쿼리 결과 캐싱 (1시간 TTL)
     * 캐시 히트 시: 2-3ms
     * 캐시 미스 시: 실제 쿼리 실행 (30-40ms)
     */
    @Cacheable(
            cacheNames = "nql_queries",
            key = "T(com.newsquery.cache.NQLCacheKeyGenerator).generateKey(#nql, #page)",
            sync = true
    )
    public NewsSearchResponse getCachedQueryResult(String nql, int page) {
        // 실제 구현은 QueryController에서 호출
        // 이 메서드는 캐시 미스 시에만 호출됨
        return null;
    }

    /**
     * 벡터 임베딩 캐싱 (24시간 TTL)
     * 임베딩 API 호출을 피하기 위해 사용
     */
    @Cacheable(
            cacheNames = "embeddings",
            key = "T(com.newsquery.cache.NQLCacheKeyGenerator).generateEmbeddingKey(#text)",
            sync = true
    )
    public float[] getCachedEmbedding(String text) {
        // 실제 구현은 EmbeddingClient에서 호출
        return null;
    }

    /**
     * GROUP BY 결과 캐싱 (5분 TTL)
     * 집계 결과를 캐시하여 빠른 재검색 지원
     */
    @Cacheable(
            cacheNames = "groupby_results",
            key = "T(com.newsquery.cache.NQLCacheKeyGenerator).generateGroupByKey(#nql, #groupByField)",
            sync = true
    )
    public NewsSearchResponse getCachedGroupByResult(String nql, String groupByField) {
        return null;
    }

    /**
     * 캐시 무효화 (데이터 업데이트 시)
     */
    @CacheEvict(cacheNames = "nql_queries", allEntries = true)
    public void invalidateAllQueries() {
        // 캐시 전체 삭제 (Elasticsearch 인덱스 업데이트 시)
    }

    @CacheEvict(cacheNames = "groupby_results", allEntries = true)
    public void invalidateGroupByCache() {
        // GROUP BY 캐시 삭제
    }

    @CacheEvict(cacheNames = "embeddings", allEntries = true)
    public void invalidateEmbeddingCache() {
        // 임베딩 캐시 삭제
    }
}
