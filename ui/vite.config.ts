/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The browser must see one origin, as behind the edge in production: the dev server serves the UI and proxies the
// two API prefixes to the local gateway. `xfwd` adds the X-Forwarded-* headers the gateway passes on to `auth`.
const gateway = process.env.GATEWAY_URL ?? 'http://localhost:8080'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // IPv4 on purpose: `backend` (Java) resolves localhost to 127.0.0.1 and reads the issuer through this server.
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: gateway, xfwd: true },
      '/auth': { target: gateway, xfwd: true },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})
