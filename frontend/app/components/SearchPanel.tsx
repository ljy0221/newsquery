"use client";

import { useState } from "react";
import NewsCard from "./NewsCard";

export interface NewsHit {
  id: string;
  title: string;
  source: string;
  sentiment: string;
  country: string;
  publishedAt: string;
  score: number;
  url: string;
}

interface SearchResult {
  total: number;
  hits: NewsHit[];
}

const EXAMPLE_QUERIES = [
  '*',
  'keyword("AI chip")',
  'keyword("HBM") * 2.0 AND sentiment != "negative"',
  'source IN ["Reuters", "BBC"]',
  'country == "KR"',
  'publishedAt >= "2026-02-01" AND sentiment == "positive"',
];

const SYNTAX_ROWS = [
  ['keyword("term")', '본문/제목 키워드 검색'],
  ['keyword("term") * 2.0', '가중치 부여 (boost)'],
  ['sentiment == "positive"', 'positive / negative / neutral'],
  ['source IN ["Reuters","BBC"]', '출처 필터'],
  ['country == "KR"', '국가 코드 필터'],
  ['publishedAt >= "YYYY-MM-DD"', '날짜 범위 필터'],
  ['A AND B', '교집합'],
  ['A OR B', '합집합'],
  ['!A', '부정 (NOT)'],
  ['*', '전체 검색 (match_all)'],
];

const PAGE_SIZE = 20;
const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export default function SearchPanel({ initialNql }: { initialNql?: string }) {
  const [nql, setNql] = useState(
    initialNql ?? 'keyword("HBM") * 1.5 AND sentiment != "negative"'
  );
  const [result, setResult] = useState<SearchResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [showDsl, setShowDsl] = useState(false);
  const [showSyntax, setShowSyntax] = useState(false);
  const [page, setPage] = useState(0);

  const doSearch = async (targetPage: number) => {
    if (!nql.trim()) return;
    setLoading(true);
    setError(null);
    try {
      const res = await fetch(`${API_URL}/api/query`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ nql, page: targetPage }),
      });

      if (!res.ok) {
        const msg = await res.text();
        setError(msg);
        return;
      }

      const data: SearchResult = await res.json();
      setResult(data);
      setPage(targetPage);
    } catch {
      setError("서버에 연결할 수 없습니다. 백엔드(localhost:8080)가 실행 중인지 확인해주세요.");
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = () => doSearch(0);

  const totalPages = result ? Math.max(1, Math.ceil(result.total / PAGE_SIZE)) : 1;

  return (
    <div className="space-y-6">
      {/* Query Input */}
      <div className="bg-white dark:bg-gray-900 rounded-xl shadow-sm border border-gray-200 dark:border-gray-800 p-6">
        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
          NQL Query
        </label>
        <textarea
          value={nql}
          onChange={(e) => setNql(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) handleSearch();
          }}
          className="w-full font-mono text-sm rounded-lg border border-gray-300 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 text-gray-900 dark:text-gray-100 p-3 focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
          rows={3}
          placeholder='keyword("HBM") * 1.5 AND sentiment != "negative"'
          spellCheck={false}
        />

        {/* Example queries */}
        <div className="flex flex-wrap gap-2 mt-3">
          {EXAMPLE_QUERIES.map((q) => (
            <button
              key={q}
              onClick={() => setNql(q)}
              className="text-xs px-2 py-1 rounded-full bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-300 hover:bg-blue-100 dark:hover:bg-blue-900/60 font-mono transition-colors"
            >
              {q}
            </button>
          ))}
        </div>

        <div className="flex items-center gap-3 mt-4">
          <button
            onClick={handleSearch}
            disabled={loading}
            className="px-5 py-2 bg-blue-600 hover:bg-blue-700 disabled:bg-blue-400 text-white rounded-lg text-sm font-medium transition-colors"
          >
            {loading ? "검색 중..." : "검색"}
          </button>
          <span className="text-xs text-gray-400">⌘+Enter</span>
          <button
            onClick={() => setShowSyntax(!showSyntax)}
            className="ml-auto text-xs px-3 py-1 rounded-full border border-gray-300 dark:border-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
          >
            {showSyntax ? "문법 숨기기" : "NQL 문법 참조"}
          </button>
        </div>

        {/* NQL syntax reference */}
        {showSyntax && (
          <div className="mt-4 rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden text-xs">
            <table className="w-full">
              <thead>
                <tr className="bg-gray-50 dark:bg-gray-800 text-left">
                  <th className="px-3 py-2 font-medium text-gray-500 dark:text-gray-400 w-1/2">표현식</th>
                  <th className="px-3 py-2 font-medium text-gray-500 dark:text-gray-400">설명</th>
                </tr>
              </thead>
              <tbody>
                {SYNTAX_ROWS.map(([expr, desc]) => (
                  <tr
                    key={expr}
                    className="border-t border-gray-100 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800/50"
                  >
                    <td
                      className="px-3 py-1.5 font-mono text-blue-600 dark:text-blue-300 cursor-pointer"
                      onClick={() => setNql(expr)}
                    >
                      {expr}
                    </td>
                    <td className="px-3 py-1.5 text-gray-600 dark:text-gray-400">{desc}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Error */}
      {error && (
        <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-4 text-sm text-red-700 dark:text-red-300 font-mono whitespace-pre-wrap">
          {error}
        </div>
      )}

      {/* Results */}
      {result && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <p className="text-sm text-gray-500 dark:text-gray-400">
              총{" "}
              <span className="font-semibold text-gray-900 dark:text-white">
                {result.total.toLocaleString()}
              </span>
              건
            </p>
            <button
              onClick={() => setShowDsl(!showDsl)}
              className="text-xs px-3 py-1 rounded-full border border-gray-300 dark:border-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
            >
              {showDsl ? "DSL 숨기기" : "응답 JSON 보기"}
            </button>
          </div>

          {showDsl && (
            <div className="bg-gray-900 rounded-lg p-4 overflow-auto max-h-56 text-xs font-mono text-green-400">
              <pre>{JSON.stringify(result, null, 2)}</pre>
            </div>
          )}

          <div className="space-y-3">
            {result.hits.length === 0 ? (
              <p className="text-center text-gray-400 py-16 text-sm">
                검색 결과가 없습니다.
              </p>
            ) : (
              result.hits.map((hit) => <NewsCard key={hit.id} hit={hit} />)
            )}
          </div>

          {/* Pagination */}
          {result.total > PAGE_SIZE && (
            <div className="flex items-center justify-center gap-4 pt-2">
              <button
                onClick={() => doSearch(page - 1)}
                disabled={page === 0 || loading}
                className="px-4 py-2 text-sm rounded-lg border border-gray-300 dark:border-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                &lt; 이전
              </button>
              <span className="text-sm text-gray-500 dark:text-gray-400">
                {page + 1} / {totalPages}
              </span>
              <button
                onClick={() => doSearch(page + 1)}
                disabled={page + 1 >= totalPages || loading}
                className="px-4 py-2 text-sm rounded-lg border border-gray-300 dark:border-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                다음 &gt;
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
