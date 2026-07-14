import { describe, expect, test } from "vitest";
import { PartnerContext } from "../../src/partner/partner-context";

describe("partner context", () => {
    test("creates partners with timesheet disabled", () => {
        const context = new PartnerContext();

        context.newPartner();

        expect(context.selectedPartner?.timesheet).toBe(false);
    });
});
