import {
  Building2,
  CalendarDays,
  ChartNoAxesCombined,
  FileText,
  FolderKanban,
  LayoutDashboard,
  MessageSquare,
  Settings,
  Timer,
  Wallet,
} from "lucide-react";
export const navigationItems = [
  ["Dashboard", LayoutDashboard],
  ["Work Assistant", MessageSquare],
  ["Time Tracker", Timer],
  ["Timesheets", CalendarDays],
  ["Projects", FolderKanban],
  ["Invoices", FileText],
  ["Payments", Wallet],
  ["Reports", ChartNoAxesCombined],
  ["Clients & Contracts", Building2],
  ["Settings", Settings],
] as const;
export type WorkspacePage = (typeof navigationItems)[number][0];
