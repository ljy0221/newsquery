"use client";

import NavTabs from "../components/NavTabs";
import StatCard from "../components/dashboard/StatCard";
import HistoryTable from "../components/dashboard/HistoryTable";
import TrendingChart from "../components/dashboard/TrendingChart";
import SavedQueryGrid from "../components/dashboard/SavedQueryGrid";
import { useDashboard } from "../hooks/useDashboard";

export default function DashboardClient() {
  const { stats, history, trending, savedQueries, loading, error, refresh } =
    useDashboard();

  return (
    <main className="min-h-screen bg-gray-50 dark:bg-gray-950">
      <div className="max-w-7xl mx-auto px-4 py-10">
        {/* 네비게이션 탭 */}
        <NavTabs />

        {/* 헤더 */}
        <header className="mb-8 flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold text-gray-900 dark:text-white tracking-tight">
              대시보드
            </h1>
            <p className="text-gray-500 dark:text-gray-400 mt-1 text-sm">
              검색 통계 및 히스토리 분석
            </p>
          </div>
          <button
            onClick={refresh}
            disabled={loading}
            className="px-4 py-2 bg-gray-200 dark:bg-gray-800 hover:bg-gray-300 dark:hover:bg-gray-700 text-gray-900 dark:text-white font-medium rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {loading ? "로딩중..." : "새로고침"}
          </button>
        </header>

        {/* 오류 배너 */}
        {error && (
          <div className="bg-red-100 dark:bg-red-900 border border-red-300 dark:border-red-700 rounded-lg p-4 mb-6">
            <p className="text-sm text-red-800 dark:text-red-300">
              ⚠️ {error}
            </p>
          </div>
        )}

        {/* 통계 카드 4열 그리드 */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
          <StatCard
            label="총 검색 횟수"
            value={stats?.totalSearches ?? 0}
            icon="🔍"
            colorClass="text-blue-600 dark:text-blue-400"
            loading={loading}
          />
          <StatCard
            label="평균 응답시간"
            value={stats?.avgResponseTimeMs ?? 0}
            unit="ms"
            icon="⚡"
            colorClass="text-green-600 dark:text-green-400"
            loading={loading}
          />
          <StatCard
            label="저장된 검색"
            value={stats?.savedQueryCount ?? 0}
            icon="💾"
            colorClass="text-purple-600 dark:text-purple-400"
            loading={loading}
          />
          <StatCard
            label="성공률"
            value={stats?.successRate ?? 0}
            unit="%"
            icon="✅"
            colorClass="text-cyan-600 dark:text-cyan-400"
            loading={loading}
          />
        </div>

        {/* 2컬럼: 히스토리 + 인기 검색어 차트 */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
          <HistoryTable items={history} />
          <TrendingChart data={trending} />
        </div>

        {/* 풀 width: 저장된 검색 */}
        <section>
          <SavedQueryGrid queries={savedQueries} />
        </section>
      </div>
    </main>
  );
}
