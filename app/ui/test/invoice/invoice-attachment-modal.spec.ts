import { describe, expect, test } from "vitest";
import {
  areAttachmentReferencesValid,
  isAttachmentReferenceValid,
} from "../../src/invoice/edit/components/modals/invoice-attachment-modal";

describe("invoice attachment modal validation", () => {
  test("accepts empty attachment lists", () => {
    expect(areAttachmentReferencesValid([])).toBe(true);
  });

  test("rejects embedded file attachments without an ID", () => {
    expect(
      isAttachmentReferenceValid({
        ID: undefined,
        Attachment: {
          EmbeddedDocumentBinaryObject: {
            __mimeCode: "application/pdf",
            __filename: "invoice.pdf",
            value: "ZmFrZQ==",
          },
        },
      }),
    ).toBe(false);
  });

  test("accepts embedded file attachments with an ID", () => {
    expect(
      isAttachmentReferenceValid({
        ID: "file-1",
        Attachment: {
          EmbeddedDocumentBinaryObject: {
            __mimeCode: "application/pdf",
            __filename: "invoice.pdf",
            value: "ZmFrZQ==",
          },
        },
      }),
    ).toBe(true);
  });

  test("rejects external links without a URI", () => {
    expect(
      isAttachmentReferenceValid({
        ID: "link-1",
        Attachment: {
          ExternalReference: {
            URI: "   ",
          },
        },
      }),
    ).toBe(false);
  });

  test("accepts external links with a URI", () => {
    expect(
      isAttachmentReferenceValid({
        ID: "link-1",
        Attachment: {
          ExternalReference: {
            URI: "https://example.com/file.pdf",
          },
        },
      }),
    ).toBe(true);
  });
});
