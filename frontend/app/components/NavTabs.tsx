"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const TABS = [
  { label: "Search", href: "/" },
  { label: "Dashboard", href: "/dashboard" },
] as const;

export default function NavTabs() {
  const pathname = usePathname();

  return (
    <nav className="flex gap-1 mb-8 border-b border-gray-200 dark:border-gray-800">
      {TABS.map((tab) => {
        const isActive = pathname === tab.href;
        return (
          <Link
            key={tab.href}
            href={tab.href}
            className={[
              "px-4 py-2 text-sm font-medium rounded-t-lg transition-colors",
              isActive
                ? "text-blue-600 dark:text-blue-400 border-b-2 border-blue-600 dark:border-blue-400"
                : "text-gray-500 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white",
            ].join(" ")}
          >
            {tab.label}
          </Link>
        );
      })}
    </nav>
  );
}
