import {Attachment} from "../../../../services/peppol/ubl";
import {AlertType} from "../../../../components/alert/alert";
import {resolve} from "@aurelia/kernel";
import {InvoiceContext} from "../../../invoice-context";
import {bindable, IEventAggregator} from "aurelia";

export class AttachmentInfo {
    private invoiceContext = resolve(InvoiceContext);
    private ea: IEventAggregator = resolve(IEventAggregator);

    @bindable readOnly: boolean;
    @bindable showAttachmentModal;

    downloadAttachment(attachment: Attachment) {
        if (attachment.EmbeddedDocumentBinaryObject) {
            const source = `data:${attachment.EmbeddedDocumentBinaryObject.__mimeCode};base64,${attachment.EmbeddedDocumentBinaryObject.value}`;
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
