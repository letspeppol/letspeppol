import {describe, expect, it} from "vitest";
import {validateDownloadPeriod} from "../../src/download/downloads";

describe("validateDownloadPeriod", () => {
    it("accepts an inclusive single-day period", () => {
        expect(validateDownloadPeriod("2026-04-03", "2026-04-03")).toBeUndefined();
    });

    it("requires both dates", () => {
        expect(validateDownloadPeriod(undefined, "2026-04-03")).toBe("download.validation.required");
        expect(validateDownloadPeriod("2026-04-03", "")).toBe("download.validation.required");
    });

    it("rejects reversed dates", () => {
        expect(validateDownloadPeriod("2026-04-04", "2026-04-03")).toBe("download.validation.order");
    });

    it("rejects dates from different years", () => {
        expect(validateDownloadPeriod("2025-12-31", "2026-01-01")).toBe("download.validation.same-year");
    });
});
