import {bindable, IEventAggregator, watch} from "aurelia";
import {AdditionalDocumentReference} from "../../../../services/peppol/ubl";
import {resolve} from "@aurelia/kernel";
import {AlertType} from "../../../../components/alert/alert";

export class InvoiceAttachmentModal {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    @bindable invoiceContext;
    additionalDocumentReference: AdditionalDocumentReference[] = [];
    open = false;
    validated = false;

    showModal() {
        this.validated = false;
        if (this.invoiceContext.selectedInvoice.AdditionalDocumentReference) {
            this.additionalDocumentReference = structuredClone(this.invoiceContext.selectedInvoice.AdditionalDocumentReference);
        } else {
            this.additionalDocumentReference = [];
        }
        this.open = true;
    }

    private closeModal() {
        this.open = false;
    }

    @watch((vm) => vm.additionalDocumentReference.length)
    validate() {
        this.validated = !this.additionalDocumentReference.length || this.additionalDocumentReference.filter(item => !item.ID || (item.Attachment.ExternalReference && !item.Attachment.ExternalReference.URI)).length === 0;
    }

    saveAttachments() {
        this.invoiceContext.selectedInvoice.AdditionalDocumentReference = this.additionalDocumentReference;
        this.closeModal();
    }

    addExternalLink() {
        this.additionalDocumentReference.push({
            ID: undefined,
            DocumentDescription: undefined,
            Attachment: {
                ExternalReference: {
                    URI: undefined
                }
            }
        } as AdditionalDocumentReference);
    }

    deleteAttachment(additionalDocumentReference: AdditionalDocumentReference) {
        this.additionalDocumentReference.splice(this.additionalDocumentReference.indexOf(additionalDocumentReference), 1);
    }

    dragOver(event) {
        event.preventDefault();
        return true;
    }

    async dragDrop(e) {
        e.preventDefault();

        const file = e.dataTransfer.files[0];
        if (!file) return;
        if (file.size > (5 * (1024 ** 3))) {
            this.ea.publish('alert', {alertType: AlertType.Warning, text: "File can not be more than 5MB"});
            return;
        }
        // BR-CL-24
        if (!['application/pdf',
                'image/png',
                'image/jpeg',
                'text/csv',
                'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
                'application/vnd.oasis.opendocument.spreadsheet'
            ].includes(file.type)) {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "File type not supported"});
        }

        try {
            const dataUrl: string = await this.toBase64(file);
            const base64String = dataUrl.split(',')[1];
            this.additionalDocumentReference.push({
                ID: undefined,
                DocumentDescription: undefined,
                Attachment: {
                    EmbeddedDocumentBinaryObject: {
                        __mimeCode: file.type,
                        __filename: file.name,
                        value: base64String
                    }
                }
            } as AdditionalDocumentReference);
        } catch(error) {
            console.log(error);
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Error uploading file"});
            return false;
        }
        return true;
    }

    toBase64 = file => new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.readAsDataURL(file);
        reader.onload = () => resolve(reader.result);
        reader.onerror = reject;
    });
}
