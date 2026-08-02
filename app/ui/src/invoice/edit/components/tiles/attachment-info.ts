import {AdditionalDocumentReference, EmbeddedDocumentBinaryObject} from "../../../../services/peppol/ubl";
import {AlertType} from "../../../../components/alert/alert";
import {resolve} from "@aurelia/kernel";
import {InvoiceContext} from "../../../invoice-context";
import {bindable, IEventAggregator} from "aurelia";
import {InvoiceService} from "../../../../services/app/invoice-service";
import {I18N} from "@aurelia/i18n";

export class AttachmentInfo {
    private static readonly attachmentIconByMimeCode: Record<string, string> = {
        'text/csv': 'csv.png',
        'application/pdf': 'pdf.png',
        'image/png': 'png.png',
        'image/jpeg': 'jpg.png',
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': 'xls.png',
        'application/vnd.oasis.opendocument.spreadsheet': 'ods.png'
    };

    private invoiceContext = resolve(InvoiceContext);
    private invoiceService = resolve(InvoiceService);
    private ea: IEventAggregator = resolve(IEventAggregator);
    private readonly i18n = resolve(I18N);

    @bindable readOnly: boolean;
    @bindable showAttachmentModal;

    getAttachmentIconSrc(embeddedDocumentBinaryObject: EmbeddedDocumentBinaryObject): string {
        return AttachmentInfo.attachmentIconByMimeCode[embeddedDocumentBinaryObject.__mimeCode] ?? 'file.svg';
    }

    getAttachmentIconAlt(embeddedDocumentBinaryObject: EmbeddedDocumentBinaryObject): string {
        return embeddedDocumentBinaryObject.__mimeCode ? `${embeddedDocumentBinaryObject.__mimeCode} attachment` : 'Attachment';
    }

    getAttachmentFilenameStem(filename?: string): string {
        const extensionStart = this.getAttachmentFilenameExtensionStart(filename);
        return extensionStart === -1 ? filename ?? '' : filename.slice(0, extensionStart);
    }

    getAttachmentFilenameExtension(filename?: string): string {
        const extensionStart = this.getAttachmentFilenameExtensionStart(filename);
        return extensionStart === -1 ? '' : filename.slice(extensionStart);
    }

    private getAttachmentFilenameExtensionStart(filename?: string): number {
        if (!filename) {
            return -1;
        }

        const lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex <= 0 || lastDotIndex === filename.length - 1) {
            return -1;
        }

        return lastDotIndex;
    }

    async downloadAttachment(additionalDocumentReference: AdditionalDocumentReference) {
        const attachment = additionalDocumentReference.Attachment;
        if (attachment.EmbeddedDocumentBinaryObject) {
            let source = `data:${attachment.EmbeddedDocumentBinaryObject.__mimeCode};base64,${attachment.EmbeddedDocumentBinaryObject.value}`;
            let objectUrl: string | null = null;
            if (additionalDocumentReference.ID === 'generated_invoice') {
                if (!this.invoiceContext.selectedDocument || !this.invoiceContext.selectedDocument.id) {
                    this.ea.publish('alert', {alertType: AlertType.Warning, text: this.i18n.tr('alert.attachment.document-not-saved')});
                    return;
                }
                const blob = await this.invoiceService.downloadPdf(this.invoiceContext.selectedDocument.id).then(res => res.blob());
                objectUrl = URL.createObjectURL(blob);
                source = objectUrl;
            }
            const link = document.createElement('a');
            document.body.appendChild(link);
            link.href = source;
            link.target = '_self';
            link.download = attachment.EmbeddedDocumentBinaryObject.__filename;
            link.click();
            if (objectUrl) {
                URL.revokeObjectURL(objectUrl);
            }
            this.ea.publish('alert', {alertType: AlertType.Info, text: this.i18n.tr('alert.attachment.downloaded', {filename: attachment.EmbeddedDocumentBinaryObject.__filename})});
        }
        if (attachment.ExternalReference && attachment.ExternalReference.URI) {
            window.open(attachment.ExternalReference.URI, '_blank');
        }
    }
}
