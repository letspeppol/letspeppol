import {AdditionalDocumentReference} from "../../../../services/peppol/ubl";
import {AlertType} from "../../../../components/alert/alert";
import {resolve} from "@aurelia/kernel";
import {InvoiceContext} from "../../../invoice-context";
import {bindable, IEventAggregator} from "aurelia";
import {InvoiceService} from "../../../../services/app/invoice-service";

export class AttachmentInfo {
    private invoiceContext = resolve(InvoiceContext);
    private invoiceService = resolve(InvoiceService);
    private ea: IEventAggregator = resolve(IEventAggregator);

    @bindable readOnly: boolean;
    @bindable showAttachmentModal;

    async downloadAttachment(additionalDocumentReference: AdditionalDocumentReference) {
        const attachment = additionalDocumentReference.Attachment;
        if (attachment.EmbeddedDocumentBinaryObject) {
            let source = `data:${attachment.EmbeddedDocumentBinaryObject.__mimeCode};base64,${attachment.EmbeddedDocumentBinaryObject.value}`;
            if (additionalDocumentReference.ID === 'generated_invoice') {
                if (!this.invoiceContext.selectedDocument || !this.invoiceContext.selectedDocument.id) {
                    this.ea.publish('alert', {alertType: AlertType.Warning, text: `Document not saved. PDF render not available.`});
                    return;
                }
                const blob = await this.invoiceService.downloadPdf(this.invoiceContext.selectedDocument.id).then(res => res.blob());
                source = URL.createObjectURL(blob);
            }
            const link = document.createElement('a');
            document.body.appendChild(link);
            link.href = source;
            link.target = '_self';
            link.download = attachment.EmbeddedDocumentBinaryObject.__filename;
            link.click();
            this.ea.publish('alert', {alertType: AlertType.Info, text: `File '${attachment.EmbeddedDocumentBinaryObject.__filename}' downloaded`});
        }
        if (attachment.ExternalReference && attachment.ExternalReference.URI) {
            window.open(attachment.ExternalReference.URI, '_blank');
        }
    }

}
