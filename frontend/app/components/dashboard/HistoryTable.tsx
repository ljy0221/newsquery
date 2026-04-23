import type { QueryHistory } from "../../types/dashboard";

interface HistoryTableProps {
  items: QueryHistory[];
}

export default function HistoryTable({ items }: HistoryTableProps) {
  const displayItems = items.slice(0, 20);

  if (displayItems.length === 0) {
    return (
      <div className="bg-white dark:bg-gray-900 rounded-xl border border-gray-200 dark:border-gray-800 p-6">
        <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
          검색 히스토리
        </h3>
        <p className="text-center text-gray-500 dark:text-gray-400 py-8">
          검색 기록이 없습니다
        </p>
      </div>
    );
  }

  return (
    <div className="bg-white dark:bg-gray-900 rounded-xl border border-gray-200 dark:border-gray-800 p-6">
      <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
        검색 히스토리
      </h3>
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="border-b border-gray-200 dark:border-gray-700">
            <tr>
              <th className="text-left py-2 px-3 text-gray-600 dark:text-gray-400 font-medium">
                NQL 쿼리
              </th>
              <th className="text-right py-2 px-3 text-gray-600 dark:text-gray-400 font-medium">
                응답시간
              </th>
              <th className="text-center py-2 px-3 text-gray-600 dark:text-gray-400 font-medium">
                상태
              </th>
              <th className="text-right py-2 px-3 text-gray-600 dark:text-gray-400 font-medium">
                실행시각
              </th>
            </tr>
          </thead>
          <tbody>
            {displayItems.map((item) => {
              const latency = item.responseTimeMs;
              let latencyColor = "text-green-600 dark:text-green-400"; // < 100ms
              if (latency >= 100 && latency < 500)
                latencyColor = "text-yellow-600 dark:text-yellow-400";
              else if (latency >= 500)
                latencyColor = "text-red-600 dark:text-red-400";

              return (
                <tr
                  key={item.id}
                  className="border-b border-gray-100 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
                >
                  <td className="py-3 px-3">
                    <code
                      className="text-xs font-mono text-gray-700 dark:text-gray-300 max-w-[280px] truncate block"
                      title={item.nql}
                    >
                      {item.nql}
                    </code>
                  </td>
                  <td className={`py-3 px-3 text-right font-medium ${latencyColor}`}>
                    {latency.toFixed(1)}ms
                  </td>
                  <td className="py-3 px-3 text-center">
                    <span
                      className={[
                        "text-xs font-semibold px-2 py-1 rounded",
                        item.success
                          ? "bg-green-100 dark:bg-green-900 text-green-700 dark:text-green-300"
                          : "bg-red-100 dark:bg-red-900 text-red-700 dark:text-red-300",
                      ].join(" ")}
                    >
                      {item.success ? "성공" : "실패"}
                    </span>
                  </td>
                  <td className="py-3 px-3 text-right text-gray-500 dark:text-gray-400 text-xs">
                    {new Date(item.executedAt).toLocaleString("ko-KR")}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
