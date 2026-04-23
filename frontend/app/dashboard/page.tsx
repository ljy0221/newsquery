import { Metadata } from "next";
import DashboardClient from "./DashboardClient";

export const metadata: Metadata = {
  title: "Dashboard - N-QL Intelligence",
  description: "검색 통계 및 히스토리 대시보드",
};

export default function DashboardPage() {
  return <DashboardClient />;
}
