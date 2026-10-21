export function duration(seconds: number) {
  return [Math.floor(seconds / 3600), Math.floor(seconds / 60) % 60, Math.floor(seconds) % 60]
    .map((v) => String(v).padStart(2, "0"))
    .join(":");
}
