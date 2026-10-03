import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// Mismo mapeo que nginx.conf, contra los puertos publicados en el host, para
// `npm run dev` con el backend del compose levantado. Si se toca uno, tocar el otro.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 3000,
    proxy: {
      '/api/orders': { target: 'http://localhost:8081', rewrite: (p) => p.replace(/^\/api/, '') },
      '/api/resilience': { target: 'http://localhost:8081', rewrite: (p) => p.replace(/^\/api/, '') },
      '/api/inventory': { target: 'http://localhost:8083', rewrite: (p) => p.replace(/^\/api/, '') },
      '/api/chaos/payment': { target: 'http://localhost:8082', rewrite: () => '/chaos' },
      '/api/chaos/inventory': { target: 'http://localhost:8083', rewrite: () => '/chaos' },
      '/api/chaos/shipping': { target: 'http://localhost:8084', rewrite: () => '/chaos' },
    },
  },
})
