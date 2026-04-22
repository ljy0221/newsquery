#!/usr/bin/env python3
"""
N-QL Intelligence 성능 테스트 스크립트
- 초기 성능 측정
- 개선 후 성능 비교
"""

import requests
import json
import time
from datetime import datetime
import statistics

BASE_URL = "http://localhost:8080/api"
HEALTH_URL = f"{BASE_URL}/health"
QUERY_URL = f"{BASE_URL}/query"
METRICS_URL = "http://localhost:8080/actuator/prometheus"

test_queries = [
    {"nql": "keyword(\"AI\")", "page": 0, "name": "Simple keyword"},
    {"nql": "keyword(\"AI\") AND sentiment == \"positive\"", "page": 0, "name": "Keyword + sentiment"},
    {"nql": "keyword(\"technology\") OR keyword(\"innovation\")", "page": 0, "name": "OR query"},
    {"nql": "keyword(\"blockchain\") AND source IN [\"Reuters\", \"Bloomberg\"]", "page": 0, "name": "Keyword + source filter"},
    {"nql": "(keyword(\"AI\") * 2.0 OR keyword(\"machine learning\")) AND sentiment != \"negative\"", "page": 0, "name": "Complex boosting"},
    {"nql": "*", "page": 0, "name": "Match all"},
]

def check_health():
    """헬스 체크"""
    try:
        resp = requests.get(HEALTH_URL, timeout=5)
        return resp.status_code in (200, 503)  # 503 = DEGRADED (일부 서비스 다운)
    except Exception as e:
        print(f"❌ Health check failed: {e}")
        return False

def run_query(nql, page):
    """쿼리 실행 및 응답 시간 측정"""
    start = time.time()
    try:
        resp = requests.post(QUERY_URL, json={"nql": nql, "page": page}, timeout=10)
        elapsed = (time.time() - start) * 1000

        if resp.status_code == 200:
            data = resp.json()
            return {
                "success": True,
                "duration_ms": elapsed,
                "total_hits": data.get("total", 0),
                "result_count": len(data.get("hits", [])),
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

def run_performance_test(iterations=5):
    """성능 테스트 실행"""
    print("\n" + "="*80)
    print("N-QL Intelligence 성능 테스트")
    print(f"시간: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("="*80)

    # 헬스 체크
    print("\n[1] 시스템 상태 확인...")
    if not check_health():
        print("❌ 시스템이 준비되지 않았습니다.")
        return None
    print("✅ 시스템 상태 정상")

    results = {}

    print(f"\n[2] 성능 테스트 실행 (각 쿼리 {iterations}회)...")
    for test_case in test_queries:
        nql = test_case["nql"]
        name = test_case["name"]

        print(f"\n  {name}")
        print(f"  Query: {nql[:60]}{'...' if len(nql) > 60 else ''}")

        durations = []
        total_hits = 0
        errors = 0

        for i in range(iterations):
            result = run_query(nql, 0)
            if result["success"]:
                durations.append(result["duration_ms"])
                total_hits = result.get("total_hits", 0)
            else:
                errors += 1

            print(f"    [{i+1}/{iterations}] ", end="", flush=True)
            if result["success"]:
                print(f"✅ {result['duration_ms']:.2f}ms ({result['total_hits']} hits)")
            else:
                print(f"❌ {result['error']}")

        if durations:
            avg = statistics.mean(durations)
            min_val = min(durations)
            max_val = max(durations)
            p95 = sorted(durations)[int(len(durations) * 0.95)] if len(durations) > 1 else avg

            results[name] = {
                "query": nql,
                "success_count": len(durations),
                "error_count": errors,
                "avg_ms": avg,
                "min_ms": min_val,
                "max_ms": max_val,
                "p95_ms": p95,
                "total_hits": total_hits,
            }

            print(f"    결과: 평균 {avg:.2f}ms (Min: {min_val:.2f}ms, Max: {max_val:.2f}ms, P95: {p95:.2f}ms)")

    return results

def save_results(results, filename="performance_baseline.json"):
    """결과 저장"""
    data = {
        "timestamp": datetime.now().isoformat(),
        "results": results,
    }

    with open(filename, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)

    print(f"\n✅ 결과 저장: {filename}")
    return filename

def compare_results(baseline_file, current_file):
    """성능 비교"""
    with open(baseline_file, 'r') as f:
        baseline = json.load(f)

    with open(current_file, 'r') as f:
        current = json.load(f)

    print("\n" + "="*80)
    print("성능 비교 분석")
    print("="*80)
    print(f"기준: {baseline['timestamp']}")
    print(f"현재: {current['timestamp']}")
    print()

    improvements = 0
    regressions = 0

    for name, curr_result in current['results'].items():
        if name not in baseline['results']:
            continue

        base_result = baseline['results'][name]
        base_avg = base_result['avg_ms']
        curr_avg = curr_result['avg_ms']
        improvement_pct = ((base_avg - curr_avg) / base_avg) * 100

        print(f"{name}")
        print(f"  기준: {base_avg:.2f}ms → 현재: {curr_avg:.2f}ms")

        if improvement_pct > 0:
            print(f"  ✅ 개선: {improvement_pct:.1f}% 빨라짐")
            improvements += 1
        elif improvement_pct < 0:
            print(f"  ⚠️  저하: {abs(improvement_pct):.1f}% 느려짐")
            regressions += 1
        else:
            print(f"  ➡️  변화 없음")
        print()

    print(f"요약: {improvements}개 개선, {regressions}개 저하")

if __name__ == "__main__":
    import sys

    if len(sys.argv) > 1 and sys.argv[1] == "compare":
        if len(sys.argv) < 4:
            print("Usage: python performance_test.py compare baseline.json current.json")
            sys.exit(1)
        compare_results(sys.argv[2], sys.argv[3])
    else:
        results = run_performance_test(iterations=5)
        if results:
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            filename = f"performance_baseline_{timestamp}.json"
            save_results(results, filename)
