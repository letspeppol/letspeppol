import { describe, expect, test } from "vitest";
import { hasTimesheetAttachment } from "../../src/invoice/edit/components/tiles/attachment-info";
import { AdditionalDocumentReference } from "../../src/services/peppol/ubl";

describe("timesheet attachment matching", () => {
    test("rejects missing and unrelated attachments", () => {
        expect(hasTimesheetAttachment()).toBe(false);
        expect(hasTimesheetAttachment([])).toBe(false);
        expect(hasTimesheetAttachment([embeddedAttachment("Generated Invoice PDF")])).toBe(false);
    });

    test("accepts an embedded file case-insensitively after trimming", () => {
        expect(hasTimesheetAttachment([embeddedAttachment("  tImEsHeEt  ")])).toBe(true);
    });

    test("accepts an external link case-insensitively after trimming", () => {
        expect(hasTimesheetAttachment([externalAttachment("TIMESHEET")])).toBe(true);
    });

    test.each(["Urenstaat", "Feuille de temps", "Stundenzettel"])(
        "accepts the translated description %s",
        (description) => {
            expect(hasTimesheetAttachment([embeddedAttachment(description)])).toBe(true);
        },
    );

    test("requires actual attachment content", () => {
        expect(hasTimesheetAttachment([{
            ID: "description-only",
            DocumentDescription: "Timesheet",
        }])).toBe(false);
        expect(hasTimesheetAttachment([embeddedAttachment("Timesheet", "")])).toBe(false);
        expect(hasTimesheetAttachment([externalAttachment("Timesheet", "  ")])).toBe(false);
    });
});

function embeddedAttachment(description: string, value = "ZmFrZQ=="): AdditionalDocumentReference {
    return {
        ID: "embedded",
        DocumentDescription: description,
        Attachment: {
            EmbeddedDocumentBinaryObject: {
                __mimeCode: "application/pdf",
                __filename: "timesheet.pdf",
                value,
            },
        },
    };
}

function externalAttachment(description: string, uri = "https://example.com/timesheet.pdf"): AdditionalDocumentReference {
    return {
        ID: "external",
        DocumentDescription: description,
        Attachment: {
            ExternalReference: { URI: uri },
        },
    };
}
