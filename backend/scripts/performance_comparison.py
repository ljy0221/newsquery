#!/usr/bin/env python3
"""
Phase 1, 2, 3 성능 비교 측정 스크립트
각 Phase의 쿼리 성능을 측정하고 비교 분석
"""

import requests
import json
import time
from datetime import datetime
import statistics
from typing import List, Dict, Tuple

BASE_URL = "http://localhost:8080/api"
HEALTH_URL = f"{BASE_URL}/health"
QUERY_URL = f"{BASE_URL}/query"

# Phase별 테스트 쿼리
PHASE1_QUERIES = [
    {"nql": "keyword(\"AI\")", "page": 0, "name": "Simple keyword"},
    {"nql": "keyword(\"technology\") AND sentiment == \"positive\"", "page": 0, "name": "Keyword + sentiment"},
    {"nql": "keyword(\"AI\") OR keyword(\"machine learning\")", "page": 0, "name": "OR query"},
    {"nql": "keyword(\"blockchain\") AND source IN [\"Reuters\", \"Bloomberg\"]", "page": 0, "name": "Keyword + source"},
    {"nql": "(keyword(\"AI\") * 2.0 OR keyword(\"tech\")) AND sentiment != \"negative\"", "page": 0, "name": "Complex query"},
    {"nql": "*", "page": 0, "name": "Match all"},
]

PHASE2_QUERIES = [
    {"nql": "keyword(\"technology\") AND publishedAt BETWEEN \"2026-03-01\" AND \"2026-04-23\"", "page": 0, "name": "BETWEEN query"},
    {"nql": "source CONTAINS \"Reuters\"", "page": 0, "name": "CONTAINS pattern"},
    {"nql": "keyword(\"AI\") AND source LIKE \"tech\"", "page": 0, "name": "LIKE pattern"},
]

PHASE3_QUERIES = [
    {"nql": "keyword(\"AI\") GROUP BY category LIMIT 5", "page": 0, "name": "GROUP BY category"},
    {"nql": "keyword(\"technology\") GROUP BY sentiment", "page": 0, "name": "GROUP BY sentiment"},
    {"nql": "keyword(\"news\") BOOST recency(publishedAt) GROUP BY source LIMIT 10", "page": 0, "name": "BOOST + GROUP BY"},
]

def check_health() -> bool:
    """시스템 헬스 확인"""
    try:
        resp = requests.get(HEALTH_URL, timeout=5)
        return resp.status_code in (200, 503)
    except:
        return False

def run_query(nql: str, page: int) -> Dict:
    """쿼리 실행 및 응답 시간 측정"""
    start = time.time()
    try:
        resp = requests.post(QUERY_URL, json={"nql": nql, "page": page}, timeout=30)
        elapsed = (time.time() - start) * 1000

        if resp.status_code == 200:
            data = resp.json()
            return {
                "success": True,
                "duration_ms": elapsed,
                "total_hits": data.get("total", 0),
            }
        else:
            return {
                "success": False,
                "duration_ms": elapsed,
                "error": resp.json().get("message", "Unknown error"),
            }
    except Exception as e:
        elapsed = (time.time() - start) * 1000
        return {
            "success": False,
            "duration_ms": elapsed,
            "error": str(e),
        }

def run_performance_test(queries: List[Dict], phase_name: str, iterations: int = 3) -> Dict[str, Dict]:
    """성능 테스트 실행"""
    results = {}

    print(f"\n{'='*70}")
    print(f"🚀 {phase_name} 성능 측정 (각 쿼리 {iterations}회)")
    print(f"{'='*70}")

    for test_case in queries:
        nql = test_case["nql"]
        name = test_case["name"]

        print(f"\n📌 {name}")
        print(f"   Query: {nql[:70]}{'...' if len(nql) > 70 else ''}")

        durations = []
        errors = 0

        for i in range(iterations):
            result = run_query(nql, 0)
            if result["success"]:
                durations.append(result["duration_ms"])
                print(f"   [{i+1}/{iterations}] ✅ {result['duration_ms']:.2f}ms ({result.get('total_hits', 'N/A')} hits)")
            else:
                errors += 1
                print(f"   [{i+1}/{iterations}] ❌ {result['error']}")

        if durations:
            avg = statistics.mean(durations)
            min_val = min(durations)
            max_val = max(durations)
            stddev = statistics.stdev(durations) if len(durations) > 1 else 0
            p95 = sorted(durations)[int(len(durations) * 0.95)] if len(durations) > 1 else avg

            results[name] = {
                "query": nql,
                "success_count": len(durations),
                "error_count": errors,
                "avg_ms": avg,
                "min_ms": min_val,
                "max_ms": max_val,
                "stddev_ms": stddev,
                "p95_ms": p95,
            }

            print(f"   📊 평균: {avg:.2f}ms | Min: {min_val:.2f}ms | Max: {max_val:.2f}ms | P95: {p95:.2f}ms")

    return results

def compare_phases(results_list: List[Tuple[str, Dict]]) -> None:
    """Phase별 성능 비교"""
    print("\n" + "="*70)
    print("📊 Phase별 성능 비교")
    print("="*70)

    # 공통 쿼리만 비교 (Phase 1 기준)
    phase1_names = set(results_list[0][1].keys())

    for query_name in sorted(phase1_names):
        print(f"\n🔍 {query_name}")

        comparisons = []
        for phase_name, results in results_list:
            if query_name in results:
                avg_ms = results[query_name]["avg_ms"]
                comparisons.append((phase_name, avg_ms))

        if len(comparisons) > 1:
            baseline = comparisons[0][1]
            for phase_name, avg_ms in comparisons:
                if phase_name == "Phase 1":
                    print(f"  {phase_name}: {avg_ms:.2f}ms (기준)")
                else:
                    change_pct = ((avg_ms - baseline) / baseline) * 100
                    direction = "⬆️ " if change_pct > 0 else "⬇️ "
                    print(f"  {phase_name}: {avg_ms:.2f}ms {direction}({change_pct:+.1f}%)")

def save_results(results: Dict, filename: str) -> None:
    """결과 저장"""
    data = {
        "timestamp": datetime.now().isoformat(),
        "results": results,
    }

    with open(filename, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)

    print(f"\n✅ 결과 저장: {filename}")

def main():
    """메인 실행"""
    # 헬스 확인
    print("🔍 시스템 상태 확인 중...")
    if not check_health():
        print("❌ 시스템이 준비되지 않았습니다")
        print("💡 다음을 확인하세요:")
        print("   1. Elasticsearch가 실행 중인가?")
        print("   2. Spring Boot가 실행 중인가? (./gradlew bootRun)")
        print("   3. 샘플 데이터가 적재되었는가? (python scripts/generate_sample_data.py)")
        return

    print("✅ 시스템 상태 정상\n")

    # Phase별 성능 측정
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")

    phase1_results = run_performance_test(PHASE1_QUERIES, "Phase 1 (기본 쿼리)")
    save_results(phase1_results, f"performance_phase1_{timestamp}.json")

    phase2_results = run_performance_test(PHASE2_QUERIES, "Phase 2 (범위 & 패턴)")
    save_results(phase2_results, f"performance_phase2_{timestamp}.json")

    phase3_results = run_performance_test(PHASE3_QUERIES, "Phase 3 (집계 & 부스팅)")
    save_results(phase3_results, f"performance_phase3_{timestamp}.json")

    # 비교 분석
    compare_phases([
        ("Phase 1", phase1_results),
        ("Phase 2", phase2_results),
        ("Phase 3", phase3_results),
    ])

    # 최종 요약
    print("\n" + "="*70)
    print("📈 최종 요약")
    print("="*70)

    def get_avg(results):
        if not results:
            return 0
        return statistics.mean([r["avg_ms"] for r in results.values()])

    phase1_avg = get_avg(phase1_results)
    phase2_avg = get_avg(phase2_results)
    phase3_avg = get_avg(phase3_results)

    print(f"\nPhase 1 평균: {phase1_avg:.2f}ms")
    print(f"Phase 2 평균: {phase2_avg:.2f}ms ({(phase2_avg-phase1_avg)/phase1_avg*100:+.1f}%)")
    print(f"Phase 3 평균: {phase3_avg:.2f}ms ({(phase3_avg-phase1_avg)/phase1_avg*100:+.1f}%)")

    print("\n💾 결과가 JSON 파일로 저장되었습니다")
    print("📊 Grafana 대시보드에서도 실시간 메트릭을 확인할 수 있습니다: http://localhost:3001")

if __name__ == "__main__":
    main()
