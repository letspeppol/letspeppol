import {bindable, IEventAggregator, watch} from "aurelia";
import {AdditionalDocumentReference} from "../../../../services/peppol/ubl";
import {resolve} from "@aurelia/kernel";
import {AlertType} from "../../../../components/alert/alert";
import moment from "moment";
import {InvoiceComposer} from "../../../invoice-composer";

export class InvoiceAttachmentModal {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private readonly invoiceComposer = resolve(InvoiceComposer);
    @bindable invoiceContext;
    additionalDocumentReference: AdditionalDocumentReference[] = [];
    open = false;
    validated = false;
    isDraggingFile = false;
    generatedPdfPresent = false;
    uploadWrapper: HTMLElement;

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

    @watch((vm) => vm.additionalDocumentReference.length)
    checkGeneratedPdf() {
        this.generatedPdfPresent = this.additionalDocumentReference.length && this.additionalDocumentReference.some(item => item.ID === 'generated_invoice');
    }

    addGeneratedPdf() {
        this.additionalDocumentReference.push(...this.invoiceComposer.getAdditionalDocumentReference());
    }

    saveAttachments() {
        this.invoiceContext.selectedInvoice.AdditionalDocumentReference = this.additionalDocumentReference;
        this.closeModal();
    }

    addExternalLink() {
        this.additionalDocumentReference.push({
            ID: moment().toISOString(),
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

    dragEnter(event: DragEvent) {
        event.preventDefault();
        this.isDraggingFile = true;
    }

    dragLeave(event: DragEvent) {
        event.preventDefault();
        if (!this.uploadWrapper.contains(event.relatedTarget as Node)) {
            this.isDraggingFile = false;
        }
    }

    async dragDrop(e: DragEvent) {
        e.preventDefault();

        const file = e.dataTransfer.files[0];
        if (!file) return;
        if (file.size > (3 * (1024 ** 2))) {
            this.ea.publish('alert', {alertType: AlertType.Warning, text: "File can not be more than 3MB"});
            return;
        }
        const totalSize = this.additionalDocumentReference.filter(item => item.Attachment?.EmbeddedDocumentBinaryObject)
            .map(item => item.Attachment.EmbeddedDocumentBinaryObject.value.length )
            .reduce((sum, value) => sum + value, 0)
            + ((file.size * 4) / 3);
        if (totalSize > (10 * (1024 ** 2))) {
            this.ea.publish('alert', {alertType: AlertType.Warning, text: "Sum of all file sizes can not be more than 10MB"});
            return;
        }
        // BR-CL-24
        const allowedTypes: string[] = [
            'application/pdf',
            'image/png',
            'image/jpeg',
            'text/csv',
            'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
            'application/vnd.oasis.opendocument.spreadsheet'
        ];
        if (!allowedTypes.includes(file.type as string)) {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "File type not supported"});
            return;
        }
        try {
            const dataUrl: string = await this.toBase64(file);
            const base64String = dataUrl.split(',')[1];
            this.additionalDocumentReference.push({
                ID: moment().toISOString(),
                DocumentDescription: undefined,
                Attachment: {
                    EmbeddedDocumentBinaryObject: {
                        __mimeCode: file.type,
                        __filename: file.name,
                        value: base64String
                    }
                }
            } as AdditionalDocumentReference);
            this.isDraggingFile = false;
        } catch(error) {
            console.log(error);
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Error uploading file"});
            this.isDraggingFile = false;
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
