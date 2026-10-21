export async function request<T>(
  path: string,
  body?: unknown,
  method = body === undefined ? "GET" : "POST",
): Promise<T> {
  const r = await fetch("/api" + path, {
    method,
    headers: {
      Authorization: "Bearer " + sessionStorage.getItem("token"),
      "Content-Type": "application/json",
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!r.ok) {
    const e = await r.json().catch(() => ({ message: r.statusText }));
    throw new Error(e.message || e.detail || "Request failed");
  }
  if (r.status === 204 || r.headers.get("content-length") === "0")
    return undefined as T;
  return r.json().catch(() => undefined);
}
export async function download(path: string, name: string, preview = false) {
  const r = await fetch("/api" + path, {
    headers: { Authorization: "Bearer " + sessionStorage.getItem("token") },
  });
  if (!r.ok) throw new Error("Export failed");
  const url = URL.createObjectURL(await r.blob());
  if (preview) window.open(url, "_blank", "noopener,noreferrer");
  else {
    const a = document.createElement("a");
    a.href = url;
    a.download = name;
    a.click();
  }
  setTimeout(() => URL.revokeObjectURL(url), 60000);
}
export function today() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
export function monthEnd(month: string) {
  return new Date(
    Number(month.slice(0, 4)),
    Number(month.slice(5, 7)),
    0,
  ).getDate();
}
export const usd = (value: unknown) =>
  new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(
    Number(value || 0),
  );
export function duration(seconds: number) {
  return [
    Math.floor(seconds / 3600),
    Math.floor(seconds / 60) % 60,
    Math.floor(seconds) % 60,
  ]
    .map((v) => String(v).padStart(2, "0"))
    .join(":");
}
export function weekStart(date: string, start: number) {
  const d = new Date(date + "T12:00:00");
  d.setDate(d.getDate() - ((d.getDay() + 7 - (start % 7)) % 7));
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
export const categories = [
  "Software Development",
  "Code Refactoring",
  "Repository Management",
  "Research",
  "Architecture and Design",
  "Deployment and Infrastructure",
  "Testing and Debugging",
  "Documentation",
  "Meetings and Standups",
  "Project Planning",
  "Other",
];
