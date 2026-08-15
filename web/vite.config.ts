// defineConfig comes from vitest/config, not vite, so the `test` block below typechecks.
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // The dev server proxies the API so the browser only ever talks to one origin. That is not a
    // convenience: it means the app needs no CORS configuration, and no CORS configuration means
    // no chance of shipping a permissive one. In production nginx does the same thing.
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
});
