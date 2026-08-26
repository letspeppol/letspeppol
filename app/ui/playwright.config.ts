import {defineConfig} from "@playwright/test";

export default defineConfig({
    testDir: "./e2e",
    timeout: 30_000,
    use: {
        baseURL: "http://localhost:3010",
        headless: true,
    },
    webServer: {
        command: "npx vite --port 3010 --strictPort",
        url: "http://localhost:3010",
        reuseExistingServer: true,
        timeout: 60_000,
    },
});
