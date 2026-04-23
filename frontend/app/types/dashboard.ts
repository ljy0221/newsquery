// 백엔드 SavedQuery 엔티티 대응
export interface SavedQuery {
  id: string;
  userId: string;
  nql: string;
  name: string | null;
  description: string | null;
  favorite: boolean; // Java isFavorite() → Jackson → "favorite"
  executionCount: number;
  avgResponseTimeMs: number;
  createdAt: string; // ISO LocalDateTime
  updatedAt: string;
  lastExecutedAt: string | null;
}

// 백엔드 QueryHistory 엔티티 대응
export interface QueryHistory {
  id: string;
  userId: string;
  nql: string;
  responseTimeMs: number;
  totalHits: number;
  success: boolean;
  errorMessage: string | null;
  executedAt: string; // ISO LocalDateTime
}

// /api/queries/saved/trending 응답 요소
export interface TrendingQuery {
  nql: string;
  count: number; // 실행 횟수
}

// 대시보드 통계 카드용
export interface DashboardStats {
  totalSearches: number; // history.length
  avgResponseTimeMs: number;
  savedQueryCount: number;
  successRate: number; // 0~100 (%)
}
