// E2E regression test for issue #199:
// when the Web eID native app / extension is unavailable, clicking
// "Check Web eID" on the onboarding page must show an actionable localized
// message instead of the raw library string
// "Web-eID extension is not available".
//
// The eID hardware itself is never involved on this error path — it fails at
// library startup — so we simulate an environment without Web eID by removing
// the browser integration points the library relies on.
import {test, expect, type Page} from "@playwright/test";

const RAW_LIBRARY_MESSAGE = "Web-eID extension is not available";
// English text of i18n key `webeid.native-unavailable` (Chromium default UA).
const ACTIONABLE_MESSAGE = "Web eID application not found";

async function stubNoWebeid(page: Page) {
    await page.addInitScript(() => {
        // The library probes navigator/window for the extension injection
        // points; remove them so every lookup fails as if nothing is installed.
        const nav = navigator as any;
        delete nav.WebEID;
        (window as any).WebEID = undefined;
    });
}

test("onboarding shows actionable message when Web eID is unavailable", async ({page}) => {
    await stubNoWebeid(page);
    await page.goto("/onboarding");

    // Click the real user entry point that triggers the web-eid library call.
    await page.getByRole("button", {name: /Check Web eID/i}).click();

    const alert = page.locator("#alert");
    await expect(alert).toBeVisible();
    const text = await alert.textContent();

    // The raw, misleading library string must NOT be shown (#199)…
    expect(text).not.toContain(RAW_LIBRARY_MESSAGE);
    // …and the actionable guidance must appear instead.
    expect(text).toContain(ACTIONABLE_MESSAGE);
});
