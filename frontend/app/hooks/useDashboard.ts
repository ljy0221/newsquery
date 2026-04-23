"use client";

import { useState, useEffect, useCallback } from "react";
import type {
  SavedQuery,
  QueryHistory,
  TrendingQuery,
  DashboardStats,
} from "../types/dashboard";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export interface DashboardData {
  stats: DashboardStats | null;
  history: QueryHistory[];
  trending: TrendingQuery[];
  savedQueries: SavedQuery[];
  loading: boolean;
  error: string | null;
  refresh: () => void;
}

export function useDashboard(): DashboardData {
  const [history, setHistory] = useState<QueryHistory[]>([]);
  const [trending, setTrending] = useState<TrendingQuery[]>([]);
  const [savedQueries, setSavedQueries] = useState<SavedQuery[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchAll = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      // 3개 엔드포인트 병렬 fetch
      const [historyRes, trendingRes, savedRes] = await Promise.all([
        fetch(`${API_URL}/api/queries/saved/history?limit=100`),
        fetch(`${API_URL}/api/queries/saved/trending?limit=5`),
        fetch(`${API_URL}/api/queries/saved`),
      ]);

      if (!historyRes.ok || !trendingRes.ok || !savedRes.ok) {
        throw new Error("데이터를 불러오는 중 오류가 발생했습니다.");
      }

      const [historyData, trendingData, savedData] = await Promise.all([
        historyRes.json() as Promise<QueryHistory[]>,
        trendingRes.json() as Promise<TrendingQuery[]>,
        savedRes.json() as Promise<SavedQuery[]>,
      ]);

      setHistory(historyData);
      setTrending(trendingData);
      setSavedQueries(savedData);
    } catch (e) {
      setError(e instanceof Error ? e.message : "알 수 없는 오류");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchAll();
  }, [fetchAll]);

  // stats는 history에서 클라이언트 측에서 계산
  const stats: DashboardStats | null =
    !loading && !error
      ? {
          totalSearches: history.length,
          avgResponseTimeMs:
            history.length > 0
              ? Math.round(
                  history.reduce((sum, h) => sum + h.responseTimeMs, 0) /
                    history.length
                )
              : 0,
          savedQueryCount: savedQueries.length,
          successRate:
            history.length > 0
              ? Math.round(
                  (history.filter((h) => h.success).length / history.length) *
                    100
                )
              : 0,
        }
      : null;

  return {
    stats,
    history,
    trending,
    savedQueries,
    loading,
    error,
    refresh: fetchAll,
  };
}
