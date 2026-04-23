#!/usr/bin/env python3
"""
부하 테스트 스크립트 - 동시성 QPS/레이턴시 측정
기존 performance_comparison.py의 패턴을 상속하여 Ramp-up 테스트 구현

실행:
  python scripts/load_test.py

결과:
  measurements/load_test_results.json
  콘솔 테이블 출력
"""

import requests
import json
import time
import statistics
import threading
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime
from typing import List, Dict
import os

BASE_URL = "http://localhost:8080/api"
HEALTH_URL = f"{BASE_URL}/health"
QUERY_URL = f"{BASE_URL}/query"

# Phase별 대표 쿼리 혼합 (기존 performance_comparison.py와 동일)
LOAD_TEST_QUERIES = [
    {"nql": "keyword(\"AI\")", "page": 0, "name": "Simple keyword"},
    {"nql": "keyword(\"technology\") AND sentiment == \"positive\"", "page": 0, "name": "Keyword + sentiment"},
    {"nql": "keyword(\"AI\") OR keyword(\"machine learning\")", "page": 0, "name": "OR query"},
    {"nql": "keyword(\"technology\") AND publishedAt BETWEEN \"2026-03-01\" AND \"2026-04-23\"", "page": 0, "name": "BETWEEN query"},
    {"nql": "keyword(\"AI\") GROUP BY category LIMIT 5", "page": 0, "name": "GROUP BY category"},
]

def check_health() -> bool:
    """시스템 헬스 확인"""
    try:
        resp = requests.get(HEALTH_URL, timeout=5)
        return resp.status_code in (200, 503)
    except Exception as e:
        print(f"❌ Health check failed: {e}")
        return False

def run_concurrent_load(
    concurrent_users: int,
    total_requests: int,
    queries: List[Dict]
) -> Dict:
    """
    동시 요청 실행 및 성능 측정

    Args:
        concurrent_users: 동시 스레드 수
        total_requests: 전체 요청 수
        queries: 순환 사용할 쿼리 목록

    Returns:
        QPS, 레이턴시 통계를 포함한 딕셔너리
    """
    results = []
    errors = []
    lock = threading.Lock()

    def worker(query_idx: int):
        """워커 스레드 - 단일 요청 실행"""
        query = queries[query_idx % len(queries)]
        start = time.perf_counter()
        try:
            resp = requests.post(QUERY_URL, json=query, timeout=10)
            elapsed_ms = (time.perf_counter() - start) * 1000
            success = resp.status_code == 200
        except Exception as e:
            elapsed_ms = (time.perf_counter() - start) * 1000
            success = False

        with lock:
            if success:
                results.append({
                    "elapsed_ms": elapsed_ms,
                    "query": query["name"],
                    "success": True
                })
            else:
                errors.append({
                    "elapsed_ms": elapsed_ms,
                    "query": query["name"],
                    "success": False
                })

    # 벽시계 기준 전체 처리 시간
    wall_start = time.perf_counter()

    # ThreadPoolExecutor로 동시 요청 발송
    with ThreadPoolExecutor(max_workers=concurrent_users) as executor:
        futures = [executor.submit(worker, i) for i in range(total_requests)]
        completed = 0
        for f in as_completed(futures):
            completed += 1
            if completed % max(1, total_requests // 10) == 0:
                progress = int((completed / total_requests) * 100)
                print(f"  Progress: {progress}% ({completed}/{total_requests})")
            try:
                f.result()
            except Exception as e:
                print(f"  ⚠️ Worker error: {e}")

    wall_elapsed = time.perf_counter() - wall_start

    # 통계 계산
    all_latencies = sorted([r["elapsed_ms"] for r in results])
    n = len(all_latencies)

    if n == 0:
        print("❌ No successful requests!")
        return None

    # 백분위수 계산
    p50_idx = max(0, int(n * 0.50) - 1)
    p95_idx = max(0, int(n * 0.95) - 1)
    p99_idx = max(0, int(n * 0.99) - 1)

    return {
        "timestamp": datetime.now().isoformat(),
        "concurrent_users": concurrent_users,
        "total_requests": total_requests,
        "successful": len(results),
        "errors": len(errors),
        "error_rate_pct": (len(errors) / total_requests * 100) if total_requests > 0 else 0,
        "wall_time_sec": wall_elapsed,
        "qps": total_requests / wall_elapsed,
        "latency_min_ms": min(all_latencies) if n > 0 else None,
        "latency_p50_ms": all_latencies[p50_idx] if n > 0 else None,
        "latency_avg_ms": statistics.mean(all_latencies) if n > 0 else None,
        "latency_p95_ms": all_latencies[p95_idx] if n > 0 else None,
        "latency_p99_ms": all_latencies[p99_idx] if n > 0 else None,
        "latency_max_ms": max(all_latencies) if n > 0 else None,
        "latency_stdev_ms": statistics.stdev(all_latencies) if n > 1 else 0,
    }

def print_result_summary(result: Dict):
    """결과 요약 출력"""
    if result is None:
        return

    print(f"\n📊 결과 (동시: {result['concurrent_users']}, 요청: {result['total_requests']})")
    print(f"  성공률: {result['successful']}/{result['total_requests']} "
          f"({100 - result['error_rate_pct']:.1f}%)")
    print(f"  QPS: {result['qps']:.2f} req/s")
    print(f"  응답시간 (ms):")
    print(f"    Min:  {result['latency_min_ms']:.2f}")
    print(f"    P50:  {result['latency_p50_ms']:.2f}")
    print(f"    Avg:  {result['latency_avg_ms']:.2f}")
    print(f"    P95:  {result['latency_p95_ms']:.2f}")
    print(f"    P99:  {result['latency_p99_ms']:.2f}")
    print(f"    Max:  {result['latency_max_ms']:.2f}")
    print(f"  처리 시간: {result['wall_time_sec']:.2f}초")

def run_load_test_suite():
    """Ramp-up 부하 테스트 시작"""
    print("\n" + "=" * 70)
    print("🔥 부하 테스트 시작 (Ramp-up Pattern)")
    print("=" * 70)

    configs = [
        {"concurrent": 1, "total": 30, "label": "순차 (베이스라인)"},
        {"concurrent": 10, "total": 100, "label": "10 동시 사용자"},
        {"concurrent": 20, "total": 200, "label": "20 동시 사용자"},
        {"concurrent": 50, "total": 500, "label": "50 동시 사용자"},
    ]

    all_results = []

    for cfg in configs:
        print(f"\n[{cfg['label']}] 시작...")
        result = run_concurrent_load(cfg["concurrent"], cfg["total"], LOAD_TEST_QUERIES)
        if result:
            result["label"] = cfg["label"]
            all_results.append(result)
            print_result_summary(result)

            # 다음 테스트 전 쿨다운 (Redis 캐시 워밍 상태 유지)
            if cfg != configs[-1]:
                print("  대기중... (캐시 안정화)")
                time.sleep(2)

    # 결과 파일 저장
    os.makedirs("measurements", exist_ok=True)

    output_file = f"measurements/load_test_results.json"
    with open(output_file, "w") as f:
        json.dump(all_results, f, indent=2)

    print(f"\n✅ 결과 저장: {output_file}")

    # 비교 테이블 출력
    print("\n" + "=" * 100)
    print("📈 성능 비교 요약")
    print("=" * 100)
    print(f"{'동시성':<15} {'QPS':<12} {'P50':<12} {'P95':<12} {'P99':<12} {'에러율':<10}")
    print("-" * 100)
    for result in all_results:
        print(
            f"{result['concurrent_users']:<15} "
            f"{result['qps']:<12.2f} "
            f"{result['latency_p50_ms']:<12.2f} "
            f"{result['latency_p95_ms']:<12.2f} "
            f"{result['latency_p99_ms']:<12.2f} "
            f"{result['error_rate_pct']:<10.2f}%"
        )
    print("=" * 100)

    return all_results

def main():
    """메인 진입점"""
    if not check_health():
        print("❌ Spring Boot 서버가 응답하지 않습니다.")
        print(f"   {HEALTH_URL} 확인해주세요.")
        return

    print("✅ 서버 헬스 확인 완료")

    try:
        run_load_test_suite()
    except KeyboardInterrupt:
        print("\n⚠️ 테스트 중단됨 (Ctrl+C)")
    except Exception as e:
        print(f"❌ 오류 발생: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    main()
