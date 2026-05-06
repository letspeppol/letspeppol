import {AdditionalDocumentReference} from "../../../../services/peppol/ubl";
import {AlertType} from "../../../../components/alert/alert";
import {resolve} from "@aurelia/kernel";
import {InvoiceContext} from "../../../invoice-context";
import {bindable, IEventAggregator} from "aurelia";
import {InvoiceService} from "../../../../services/app/invoice-service";
import {I18N} from "@aurelia/i18n";

export class AttachmentInfo {
    private invoiceContext = resolve(InvoiceContext);
    private invoiceService = resolve(InvoiceService);
    private ea: IEventAggregator = resolve(IEventAggregator);
    private readonly i18n = resolve(I18N);

    @bindable readOnly: boolean;
    @bindable showAttachmentModal;

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
