import { applicationConfig } from "@/config/application";
const apiBase = applicationConfig.apiBaseUrl;
export async function request<T>(
  path: string,
  body?: unknown,
  method = body === undefined ? "GET" : "POST",
): Promise<T> {
  const r = await fetch(apiBase + "/api" + path, {
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
  if (r.status === 204 || r.headers.get("content-length") === "0") return undefined as T;
  return r.json().catch(() => undefined);
}
export async function download(path: string, name: string, preview = false) {
  const r = await fetch(apiBase + "/api" + path, {
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
