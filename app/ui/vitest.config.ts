import { fileURLToPath } from "node:url";
import { mergeConfig, defineConfig } from "vitest/config";
import viteConfig from "./vite.config";

export default defineConfig(env => mergeConfig(
  viteConfig(env),
  defineConfig({
    test: {
      environment: "jsdom",
      watch: false,
      root: fileURLToPath(new URL("./", import.meta.url)),
      setupFiles: ["./test/setup.ts"]
    },
  }),
));