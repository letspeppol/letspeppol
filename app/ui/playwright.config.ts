import {defineConfig, devices} from "@playwright/test";

/**
 * Playwright configuration for Let's Peppol UI tests.
 *
 * Environment variables:
 * - UI_PORT: Host port the UI is running on (default: 3010)
 * - BASE_URL: Full base URL for tests (default: http://localhost:${UI_PORT})
 */
export default defineConfig({
    testDir: "./e2e",
    timeout: 30_000,
    fullyParallel: true,
    forbidOnly: !!process.env.CI,
    retries: process.env.CI ? 2 : 0,
    workers: process.env.CI ? 1 : undefined,
    reporter: "html",
    use: {
        baseURL: process.env.BASE_URL || `http://localhost:${process.env.UI_PORT || 3010}`,
        headless: true,
        screenshot: "only-on-failure",
        trace: "retain-on-failure",
    },
    projects: [
        {
            name: "chromium",
            use: {...devices["Desktop Chrome"]},
        },
    ],
    webServer: {
        command: `npx vite --port ${process.env.UI_PORT || 3010} --strictPort`,
        url: `http://localhost:${process.env.UI_PORT || 3010}`,
        reuseExistingServer: !process.env.CI,
        timeout: 60_000,
    },
});
