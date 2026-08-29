import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  // O alvo do proxy sai do ambiente para que outra aplicação ocupando a 8080
  // não obrigue a editar um arquivo versionado. O padrão continua sendo 8080;
  // quem precisar troca em .env.local, que não vai para o repositório.
  const env = loadEnv(mode, process.cwd(), "");
  const api = env.API_URL ?? "http://localhost:8080";

  return {
    plugins: [react()],
    server: {
      port: 5173,
      // Encaminha a API para o backend em desenvolvimento, evitando CORS
      // e mantendo o front sem saber a porta do servidor.
      proxy: {
        "/api": {
          target: api,
          changeOrigin: true,
        },
      },
    },
  };
});
