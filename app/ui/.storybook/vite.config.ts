import { defineConfig } from 'vite';
import { nodePolyfills } from 'vite-plugin-node-polyfills';
import aurelia from '@aurelia/vite-plugin';

const aureliaRuntime = [
  'aurelia',
  '@aurelia/expression-parser',
  '@aurelia/fetch-client',
  '@aurelia/i18n',
  '@aurelia/kernel',
  '@aurelia/metadata',
  '@aurelia/platform',
  '@aurelia/platform-browser',
  '@aurelia/route-recognizer',
  '@aurelia/router',
  '@aurelia/runtime',
  '@aurelia/runtime-html',
  '@aurelia/storybook',
  '@aurelia/storybook/preview',
  '@aurelia/template-compiler',
];

export default defineConfig({
  esbuild: {
    target: 'es2022',
  },
  optimizeDeps: {
    exclude: aureliaRuntime,
    include: ['i18next'],
  },
  plugins: [
    aurelia({
      useDev: true,
    }),
    nodePolyfills(),
  ],
});
