import vue from "@vitejs/plugin-vue";
import { defineConfig, loadEnv } from "vite";

// In dev, the Vite server proxies REST and WebSocket traffic to the Spring Boot backend.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "VITE_");
  // Docker supplies http://backend:8080; local Spring Boot listens on :8080.
  const target = env.VITE_API_PROXY_TARGET || "http://localhost:8080";

  return {
    plugins: [vue()],
    server: {
      port: 5173,
      strictPort: true,
      proxy: {
        "/api": { target },
        "/ws": { target, ws: true },
      },
    },
    test: { environment: "node" },
  };
});
