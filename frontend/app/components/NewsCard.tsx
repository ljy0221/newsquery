import { NewsHit } from "./SearchPanel";

const SENTIMENT_STYLE: Record<string, string> = {
  positive: "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300",
  negative: "bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300",
  neutral: "bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300",
};

export default function NewsCard({ hit }: { hit: NewsHit }) {
  const sentimentStyle = SENTIMENT_STYLE[hit.sentiment] ?? SENTIMENT_STYLE.neutral;
  const date = hit.publishedAt
    ? new Date(hit.publishedAt).toLocaleDateString("ko-KR")
    : "";

  return (
    <div className="bg-white dark:bg-gray-900 rounded-xl border border-gray-200 dark:border-gray-800 p-5 hover:shadow-md transition-shadow">
      <a
        href={hit.url || "#"}
        target="_blank"
        rel="noopener noreferrer"
        className="font-medium text-gray-900 dark:text-white hover:text-blue-600 dark:hover:text-blue-400 leading-snug block"
      >
        {hit.title || "(제목 없음)"}
      </a>
      <div className="flex flex-wrap items-center gap-2 mt-3 text-xs">
        <span className="font-medium text-gray-500 dark:text-gray-400">{hit.source}</span>
        <Dot />
        <span className={`px-2 py-0.5 rounded-full font-medium ${sentimentStyle}`}>
          {hit.sentiment}
        </span>
        <Dot />
        <span className="text-gray-400">{hit.country}</span>
        <Dot />
        <span className="text-gray-400">score {hit.score.toFixed(1)}</span>
        {date && (
          <>
            <Dot />
            <span className="text-gray-400">{date}</span>
          </>
        )}
      </div>
    </div>
  );
}

function Dot() {
  return <span className="text-gray-300 dark:text-gray-700">·</span>;
}
