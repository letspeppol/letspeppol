// Regression test for issue #314: unknown URLs must render a localized
// "Page not found" view instead of an empty application shell.
import {test, expect} from "@playwright/test";

test("unknown URL shows the not-found page", async ({page}) => {
    await page.goto("/404-page");
    await expect(page.getByRole("heading", {name: /Page not found/i})).toBeVisible();
    await expect(page.getByText(/does not exist or has been moved/i)).toBeVisible();
    await expect(page.getByRole("link", {name: /Back to home/i})).toBeVisible();
});
