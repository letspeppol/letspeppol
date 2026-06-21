import {valueConverter} from "aurelia";

@valueConverter('filenameTruncate')
export class FilenameTruncateConverter {
    // Shortens a long file name for display while keeping its extension visible,
    // e.g. "very_long_invoice_name.pdf" -> "very_long_invo... .pdf"; the full name
    // stays available via a title tooltip on the element. Multi-part extensions keep
    // only the last segment (".tar.gz" -> ".gz"), which is fine for the .pdf/.xml
    // attachments this app handles.
    toView(value: string, maxLength: number = 22 /* fits the attachments card; CSS clamps the actual width */): string {
        if (!value) {
            return '';
        }
        const max = Math.floor(maxLength);
        if (!Number.isFinite(max) || max < 1) {
            return '';
        }
        if (value.length <= max) {
            return value;
        }
        const ellipsis = '...';
        // Budget too small to even fit "x...": hard-cut so the result never exceeds maxLength.
        if (max <= ellipsis.length) {
            return value.slice(0, max);
        }
        const dot = value.lastIndexOf('.');
        const hasExtension = dot > 0 && dot < value.length - 1;
        if (!hasExtension) {
            return value.slice(0, max - ellipsis.length).trimEnd() + ellipsis;
        }
        const extension = value.slice(dot); // keeps the leading dot, e.g. ".pdf"
        const stem = value.slice(0, dot);
        const stemBudget = max - extension.length - ellipsis.length - 1; // -1 for the space before the extension
        if (stemBudget >= 1) {
            return stem.slice(0, stemBudget).trimEnd() + ellipsis + ' ' + extension;
        }
        // Extension too long for a "stem... .ext" layout: still surface it (never silently
        // drop it) by keeping the first character + ellipsis + as much of the extension as fits.
        const head = value.slice(0, 1);
        const shownExtension = extension.slice(0, Math.max(0, max - head.length - ellipsis.length));
        return head + ellipsis + shownExtension;
    }
}
