interface StatCardProps {
  label: string;
  value: string | number;
  unit?: string;
  icon: string; // 텍스트 이모지 또는 SVG 문자열
  colorClass: string; // Tailwind 색상 클래스 (예: "text-blue-600")
  loading?: boolean;
}

export default function StatCard({
  label,
  value,
  unit,
  icon,
  colorClass,
  loading = false,
}: StatCardProps) {
  if (loading) {
    return (
      <div className="bg-white dark:bg-gray-900 rounded-xl border border-gray-200 dark:border-gray-800 p-6 animate-pulse">
        <div className="flex items-start justify-between">
          <div className="flex-1">
            <div className="h-4 bg-gray-300 dark:bg-gray-700 rounded w-24 mb-4" />
            <div className="h-8 bg-gray-300 dark:bg-gray-700 rounded w-32" />
          </div>
          <div className={`text-3xl ${colorClass}`}>{icon}</div>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-white dark:bg-gray-900 rounded-xl border border-gray-200 dark:border-gray-800 p-6 hover:border-gray-300 dark:hover:border-gray-700 transition-colors">
      <div className="flex items-start justify-between">
        <div className="flex-1">
          <p className="text-sm text-gray-600 dark:text-gray-400 mb-1">
            {label}
          </p>
          <p className="text-2xl font-bold text-gray-900 dark:text-white">
            {value}
            {unit && <span className="text-sm font-normal ml-1">{unit}</span>}
          </p>
        </div>
        <div className={`text-3xl ${colorClass}`}>{icon}</div>
      </div>
    </div>
  );
}
