package com.newsquery.performance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 4: 상세 쿼리 프로파일링
 * 각 단계별 실행 시간을 마이크로초 단위로 측정
 */
@Component
public class QueryProfiler {

    private static final Logger log = LoggerFactory.getLogger(QueryProfiler.class);
    private static final long SLOW_QUERY_THRESHOLD_MS = 30;  // 30ms 이상이면 느린 쿼리

    // ThreadLocal을 사용하여 요청별 독립적인 타이밍 추적
    private final ThreadLocal<Map<String, Long>> startTimes = ThreadLocal.withInitial(ConcurrentHashMap::new);
    private final ThreadLocal<Map<String, Long>> durations = ThreadLocal.withInitial(ConcurrentHashMap::new);

    /**
     * 특정 작업의 시작 시간 기록
     */
    public void start(String operation) {
        startTimes.get().put(operation, System.nanoTime());
    }

    /**
     * 특정 작업의 종료 시간 기록 및 소요 시간 계산
     */
    public long end(String operation) {
        Long startNano = startTimes.get().remove(operation);
        if (startNano == null) {
            log.warn("Operation {} not started", operation);
            return 0;
        }

        long endNano = System.nanoTime();
        long durationNano = endNano - startNano;
        long durationMs = durationNano / 1_000_000;

        durations.get().put(operation, durationMs);

        // 30ms 이상이면 로깅
        if (durationMs > SLOW_QUERY_THRESHOLD_MS) {
            log.info("⚠️  Slow operation [{}]: {}ms", operation, durationMs);
        }

        return durationMs;
    }

    /**
     * 모든 타이밍 정보 조회
     */
    public Map<String, Long> getTimings() {
        return new HashMap<>(durations.get());
    }

    /**
     * 특정 작업의 소요 시간 조회
     */
    public long getDuration(String operation) {
        return durations.get().getOrDefault(operation, 0L);
    }

    /**
     * 전체 소요 시간 조회
     */
    public long getTotalDuration() {
        return durations.get().values().stream()
                .mapToLong(Long::longValue)
                .sum();
    }

    /**
     * 타이밍 정보 초기화 (요청 처리 후)
     */
    public void clear() {
        startTimes.get().clear();
        durations.get().clear();
    }

    /**
     * 타이밍 정보 로깅
     */
    public void logTimings(String nql) {
        Map<String, Long> timings = getTimings();
        if (timings.isEmpty()) {
            return;
        }

        long totalMs = getTotalDuration();
        log.debug("📊 Query Profile [{}]", nql);
        log.debug("  Total: {}ms", totalMs);
        timings.forEach((op, duration) ->
                log.debug("    - {}: {}ms ({:.1f}%)", op, duration, (duration * 100.0 / totalMs))
        );
    }

    /**
     * 프로파일링 결과를 구조화된 형식으로 반환
     */
    public QueryProfileResult getProfileResult() {
        Map<String, Long> timings = getTimings();
        long totalMs = getTotalDuration();

        return new QueryProfileResult(timings, totalMs);
    }

    /**
     * 쿼리 프로파일링 결과 DTO
     */
    public record QueryProfileResult(Map<String, Long> timings, long totalMs) {}
}
