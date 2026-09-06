import { defineConfig } from 'vite';
import { nodePolyfills } from 'vite-plugin-node-polyfills'
import aurelia from '@aurelia/vite-plugin';

export default defineConfig({
  build: {
    rollupOptions: {
      input: {
        app: new URL('./index.html', import.meta.url).pathname,
        apiDocs: new URL('./api-docs.html', import.meta.url).pathname,
        oauth2Redirect: new URL('./oauth2-redirect.html', import.meta.url).pathname,
      },
    },
  },
  server: {
    // The three Spring services take longer to become ready than Vite. Opening the browser here
    // makes a normal debug launch look broken while KYC/app/proxy are still starting. Developers
    // can opt back in explicitly when they are running the UI on its own.
    open: process.env.VITE_OPEN_BROWSER === 'true',
    // Transform the application graph while the browser is still reaching the dev server. This is
    // especially useful behind the local TLS proxy, where a cold transform waterfall is expensive.
    warmup: {
      clientFiles: ['./src/**/*.{ts,html,css,json}'],
    },
    port: 9000,
    proxy: {
      '/kyc': {
        target: 'http://localhost:8084',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/kyc/, ''),
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq, req) => {
            proxyReq.removeHeader('origin');
            proxyReq.removeHeader('referer');
            proxyReq.setHeader('X-Forwarded-Prefix', '/kyc');
            proxyReq.setHeader(
                'X-Forwarded-Host',
                req.headers['x-forwarded-host'] || 'letspeppol.httpsonlan.com:3001'
            );
            proxyReq.setHeader('X-Forwarded-Proto', 'https');
          });
        },
      },
      '/app': {
        target: 'http://localhost:8085',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/app/, ''),
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => {
            proxyReq.removeHeader('origin');
            proxyReq.removeHeader('referer');
          });
        },
      },
      '/proxy': {
        target: 'http://localhost:8086',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/proxy/, ''),
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => {
            proxyReq.removeHeader('origin');
            proxyReq.removeHeader('referer');
          });
        },
      },
    },
  },
  esbuild: {
    target: 'es2022'
  },
  plugins: [
    aurelia({
      useDev: true
    }),
    nodePolyfills(),
  ],
});
