import SearchPanel from "./components/SearchPanel";
import NavTabs from "./components/NavTabs";

export default async function Home({
  searchParams,
}: {
  searchParams: Promise<{ nql?: string }>;
}) {
  const params = await searchParams;
  const initialNql = params.nql ? decodeURIComponent(params.nql) : undefined;

  return (
    <main className="min-h-screen bg-gray-50 dark:bg-gray-950">
      <div className="max-w-5xl mx-auto px-4 py-10">
        <NavTabs />
        <header className="mb-8">
          <h1 className="text-3xl font-bold text-gray-900 dark:text-white tracking-tight">
            N-QL Intelligence
          </h1>
          <p className="text-gray-500 dark:text-gray-400 mt-1 text-sm">
            전문가용 뉴스 랭킹 엔진 &mdash; NQL로 뉴스를 직접 쿼리하세요
          </p>
        </header>
        <SearchPanel initialNql={initialNql} />
      </div>
    </main>
  );
}
