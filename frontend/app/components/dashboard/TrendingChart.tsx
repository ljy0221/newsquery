"use client";

import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Cell,
} from "recharts";
import type { TrendingQuery } from "../../types/dashboard";

interface TrendingChartProps {
  data: TrendingQuery[];
}

// XAxis tick 렌더 함수 (긴 텍스트 truncate + rotate)
const CustomTick = ({ x, y, payload }: any) => {
  const text = payload.value as string;
  const truncated = text.length > 20 ? text.slice(0, 20) + "…" : text;
  return (
    <g transform={`translate(${x},${y})`}>
      <text
        x={0}
        y={0}
        dy={16}
        textAnchor="end"
        fill="#6b7280"
        fontSize={11}
        transform="rotate(-35)"
      >
        {truncated}
      </text>
    </g>
  );
};

export default function TrendingChart({ data }: TrendingChartProps) {
  if (data.length === 0) {
    return (
      <div className="bg-white dark:bg-gray-900 rounded-xl border border-gray-200 dark:border-gray-800 p-6">
        <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
          인기 검색어
        </h3>
        <p className="text-center text-gray-500 dark:text-gray-400 py-12">
          데이터가 없습니다
        </p>
      </div>
    );
  }

  return (
    <div className="bg-white dark:bg-gray-900 rounded-xl border border-gray-200 dark:border-gray-800 p-6">
      <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
        인기 검색어 Top 5
      </h3>
      <ResponsiveContainer width="100%" height={220}>
        <BarChart data={data} margin={{ top: 8, right: 8, bottom: 60, left: 0 }}>
          <CartesianGrid
            strokeDasharray="3 3"
            stroke="#374151"
            vertical={false}
          />
          <XAxis
            dataKey="nql"
            tick={<CustomTick />}
            interval={0}
            height={80}
          />
          <YAxis
            allowDecimals={false}
            tick={{ fontSize: 11, fill: "#6b7280" }}
          />
          <Tooltip
            contentStyle={{
              backgroundColor: "#1f2937",
              border: "1px solid #374151",
              borderRadius: "8px",
              color: "#f9fafb",
              fontSize: "12px",
            }}
            formatter={(value: any) => [`${value}회`, "검색 횟수"]}
            cursor={{ fill: "rgba(59, 130, 246, 0.1)" }}
          />
          <Bar dataKey="count" radius={[4, 4, 0, 0]}>
            {data.map((_, index) => (
              <Cell
                key={`cell-${index}`}
                fill={index === 0 ? "#3b82f6" : "#60a5fa"}
              />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
