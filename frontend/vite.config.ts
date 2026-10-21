import { defineConfig } from "vitest/config";
import { loadEnv } from "vite";
import { fileURLToPath } from "node:url";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
export default defineConfig(({ mode }) => ({
  base: loadEnv(mode, ".", "VITE_").VITE_BASE_PATH || "/",
  resolve: { alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) } },
  plugins: [react(), tailwindcss()],
  server: {
    proxy: { "/api": loadEnv(mode, ".", "VITE_").VITE_DEV_API_TARGET || "http://127.0.0.1:8080" },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test-setup.ts"],
    testTimeout: 15000,
  },
}));
