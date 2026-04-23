"use client";

import { useRouter } from "next/navigation";
import type { SavedQuery } from "../../types/dashboard";

interface SavedQueryGridProps {
  queries: SavedQuery[];
}

export default function SavedQueryGrid({ queries }: SavedQueryGridProps) {
  const router = useRouter();

  const handleExecute = (nql: string) => {
    router.push(`/?nql=${encodeURIComponent(nql)}`);
  };

  if (queries.length === 0) {
    return (
      <div className="bg-white dark:bg-gray-900 rounded-xl border border-gray-200 dark:border-gray-800 p-6">
        <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
          저장된 검색
        </h3>
        <p className="text-center text-gray-500 dark:text-gray-400 py-8">
          저장된 검색이 없습니다
        </p>
      </div>
    );
  }

  return (
    <div>
      <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
        저장된 검색
      </h3>
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {queries.map((query) => (
          <div
            key={query.id}
            className="bg-white dark:bg-gray-900 rounded-xl border border-gray-200 dark:border-gray-800 p-5 hover:border-blue-300 dark:hover:border-blue-700 transition-colors"
          >
            <div className="flex items-start justify-between mb-3">
              <div className="flex-1 min-w-0">
                <h4 className="font-semibold text-gray-900 dark:text-white truncate text-sm">
                  {query.name || query.nql.substring(0, 40)}
                </h4>
                {query.description && (
                  <p className="text-xs text-gray-500 dark:text-gray-400 mt-1 line-clamp-2">
                    {query.description}
                  </p>
                )}
              </div>
              {query.favorite && <span className="text-lg ml-2">⭐</span>}
            </div>

            <code className="text-xs font-mono text-gray-600 dark:text-gray-300 block truncate bg-gray-50 dark:bg-gray-800 p-2 rounded mb-3">
              {query.nql}
            </code>

            <div className="flex items-center justify-between text-xs text-gray-600 dark:text-gray-400 mb-3">
              <span>실행: {query.executionCount}회</span>
              <span>{query.avgResponseTimeMs.toFixed(0)}ms</span>
            </div>

            <button
              onClick={() => handleExecute(query.nql)}
              className="w-full px-3 py-2 bg-blue-600 hover:bg-blue-700 text-white font-medium rounded-lg text-sm transition-colors"
            >
              실행
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}
