import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  const orderUrl    = env.VITE_ORDER_SERVICE_URL    || 'http://localhost:8082'
  const deliveryUrl = env.VITE_DELIVERY_SERVICE_URL || 'http://localhost:8083'
  const assignUrl   = env.VITE_ASSIGNMENT_SERVICE_URL || 'http://localhost:8085'

  return {
    plugins: [
      react({
        // Use Babel classic runtime — avoids any eval-based fast-refresh shims
        jsxRuntime: 'automatic',
        babel: { plugins: [] },
      }),
    ],

    // Use file-based (non-eval) source maps both in dev and prod.
    // 'cheap-module-source-map' maps to original lines without column info,
    // which is good enough for a dev harness and never uses eval().
    build: {
      sourcemap: true,         // 'true' emits .map files — no eval in prod
    },
    css: {
      devSourcemap: false,     // prevents inline CSS source maps (another eval source)
    },

    server: {
      proxy: {
        '/api/orders': {
          target: orderUrl,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api\/orders/, '/orders'),
        },
        '/api/delivery': {
          target: deliveryUrl,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api\/delivery/, '/deliveries'),
        },
        '/api/assign': {
          target: assignUrl,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api\/assign/, ''),
        },
      },
    },
  }
})
