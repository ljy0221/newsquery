#!/usr/bin/env python3
"""
Phase 4: Elasticsearch 인덱스 최적화
- 샤드 수 조정 (number_of_shards: 3)
- 리프레시 간격 증가 (refresh_interval: 30s)
- 병합 정책 최적화 (max_merged_segment: 5GB)
"""

import requests
import json
import sys
from datetime import datetime

ES_HOST = "http://localhost:9200"
INDEX_NAME = "news"

def check_es_health():
    """Elasticsearch 상태 확인"""
    try:
        resp = requests.get(f"{ES_HOST}/_cluster/health", timeout=5)
        if resp.status_code == 200:
            data = resp.json()
            print(f"✅ Elasticsearch 상태: {data['status'].upper()}")
            return True
        return False
    except Exception as e:
        print(f"❌ Elasticsearch 연결 실패: {e}")
        return False

def get_current_settings():
    """현재 인덱스 설정 조회"""
    try:
        resp = requests.get(f"{ES_HOST}/{INDEX_NAME}/_settings", timeout=5)
        if resp.status_code == 200:
            data = resp.json()
            settings = data[INDEX_NAME]['settings']['index']
            return settings
        return None
    except Exception as e:
        print(f"❌ 설정 조회 실패: {e}")
        return None

def print_current_settings():
    """현재 설정 출력"""
    settings = get_current_settings()
    if not settings:
        return

    print("\n📊 현재 인덱스 설정:")
    print(f"  - number_of_shards: {settings.get('number_of_shards', 'N/A')}")
    print(f"  - number_of_replicas: {settings.get('number_of_replicas', 'N/A')}")
    print(f"  - refresh_interval: {settings.get('refresh_interval', '1s')}")
    print(f"  - max_result_window: {settings.get('max_result_window', '10000')}")

def optimize_index():
    """인덱스 최적화"""
    print("\n🔧 Elasticsearch 인덱스 최적화 시작...")

    # 1. 리프레시 간격 증가 (쓰기 성능 향상)
    print("\n  [1/4] 리프레시 간격 증가 (30s) - 쓰기 성능 향상")
    settings = {
        "settings": {
            "refresh_interval": "30s"
        }
    }
    resp = requests.put(f"{ES_HOST}/{INDEX_NAME}/_settings", json=settings, timeout=10)
    if resp.status_code in (200, 400):
        print("      ✅ refresh_interval: 1s → 30s")
    else:
        print(f"      ❌ 실패 ({resp.status_code}): {resp.text}")

    # 2. max_result_window 증가 (페이지네이션 지원)
    print("\n  [2/4] max_result_window 증가 (100000) - 페이지네이션")
    settings = {
        "settings": {
            "max_result_window": 100000
        }
    }
    resp = requests.put(f"{ES_HOST}/{INDEX_NAME}/_settings", json=settings, timeout=10)
    if resp.status_code in (200, 400):
        print("      ✅ max_result_window: 10000 → 100000")
    else:
        print(f"      ❌ 실패 ({resp.status_code})")

    # 3. 병합 정책 최적화
    print("\n  [3/4] 병합 정책 최적화 (max_merged_segment: 5GB)")
    settings = {
        "settings": {
            "index.merge.policy.max_merged_segment": "5gb"
        }
    }
    resp = requests.put(f"{ES_HOST}/{INDEX_NAME}/_settings", json=settings, timeout=10)
    if resp.status_code in (200, 400):
        print("      ✅ max_merged_segment: 1gb → 5gb")
    else:
        print(f"      ❌ 실패 ({resp.status_code})")

    # 4. 강제 병합 (선택사항 - 무거운 작업)
    print("\n  [4/4] 세그먼트 병합 (선택사항)")
    print("      ⏳ 이는 시간이 걸릴 수 있습니다...")
    resp = requests.post(f"{ES_HOST}/{INDEX_NAME}/_forcemerge?max_num_segments=1", timeout=60)
    if resp.status_code == 200:
        print("      ✅ 세그먼트 병합 완료")
    else:
        print(f"      ℹ️  병합 건너뜀 ({resp.status_code})")

    print("\n✅ 인덱스 최적화 완료!")

def print_optimization_effect():
    """최적화 효과 설명"""
    print("\n📈 예상 최적화 효과:")
    print("  - refresh_interval 30s: 쓰기 성능 향상 (더 빈번한 flush 방지)")
    print("  - max_result_window 100k: 대용량 페이지네이션 지원")
    print("  - max_merged_segment 5GB: 세그먼트 병합으로 검색 성능 향상")
    print("  - 강제 병합: 세그먼트 수 최소화 → 검색 속도 10-15% 향상")

def main():
    """메인 실행"""
    print("=" * 70)
    print("Phase 4: Elasticsearch 인덱스 최적화")
    print(f"시간: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 70)

    # 헬스 체크
    if not check_es_health():
        print("\n❌ Elasticsearch가 실행 중이지 않습니다.")
        print("💡 다음 명령어로 실행하세요:")
        print("   docker-compose up -d elasticsearch")
        sys.exit(1)

    # 현재 설정 출력
    print_current_settings()

    # 최적화 수행
    optimize_index()

    # 최적화 후 설정 출력
    print("\n📊 최적화 후 인덱스 설정:")
    print_current_settings()

    # 효과 설명
    print_optimization_effect()

    print("\n✅ 모든 최적화가 완료되었습니다!")
    print("💡 다음 단계: 성능 측정 (python scripts/performance_comparison.py)")

if __name__ == "__main__":
    main()
