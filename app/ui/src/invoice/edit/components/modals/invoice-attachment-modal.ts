import {bindable, IEventAggregator, watch} from "aurelia";
import {onModalEnter} from "../../../../components/util/modal-keyboard";
import {AdditionalDocumentReference} from "../../../../services/peppol/ubl";
import {resolve} from "@aurelia/kernel";
import {AlertType} from "../../../../components/alert/alert";
import moment from "moment";
import {InvoiceComposer, GENERATED_INVOICE} from "../../../invoice-composer";
import {I18N} from "@aurelia/i18n";

export class InvoiceAttachmentModal {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private readonly invoiceComposer = resolve(InvoiceComposer);
    private readonly i18n = resolve(I18N);
    @bindable invoiceContext;
    additionalDocumentReference: AdditionalDocumentReference[] = [];
    open = false;
    validated = false;
    isDraggingFile = false;
    includeGeneratedPdf = false;
    userAttachments: AdditionalDocumentReference[] = [];
    uploadWrapper: HTMLElement;
    fileInput: HTMLInputElement;

    showModal() {
        this.validated = false;
        if (this.invoiceContext.selectedInvoice.AdditionalDocumentReference) {
            this.additionalDocumentReference = structuredClone(this.invoiceContext.selectedInvoice.AdditionalDocumentReference);
        } else {
            this.additionalDocumentReference = [];
        }
        this.includeGeneratedPdf = this.additionalDocumentReference.some(item => item.ID === GENERATED_INVOICE);
        this.refreshUserAttachments();
        this.validate();
        this.open = true;
    }

    private closeModal() {
        this.open = false;
    }

    @watch((vm) => vm.additionalDocumentReference.length)
    onAttachmentsChanged() {
        this.validate();
        this.refreshUserAttachments();
    }

    validate() {
        this.validated = !this.additionalDocumentReference.length || this.additionalDocumentReference.filter(item => !item.ID || (item.Attachment.ExternalReference && !item.Attachment.ExternalReference.URI)).length === 0;
    }

    refreshUserAttachments() {
        // The generated invoice PDF is represented by its own toggle, not as a row.
        this.userAttachments = this.additionalDocumentReference.filter(item => item.ID !== GENERATED_INVOICE);
    }

    get hasUploadedPdf(): boolean {
        return this.additionalDocumentReference.some(item =>
            item.ID !== GENERATED_INVOICE
            && item.Attachment?.EmbeddedDocumentBinaryObject?.__mimeCode === 'application/pdf');
    }

    onToggleGeneratedPdf(checked: boolean) {
        this.includeGeneratedPdf = checked;
        if (checked) {
            if (!this.additionalDocumentReference.some(item => item.ID === GENERATED_INVOICE)) {
                this.additionalDocumentReference.push(...this.invoiceComposer.getAdditionalDocumentReference());
            }
        } else {
            this.additionalDocumentReference = this.additionalDocumentReference.filter(item => item.ID !== GENERATED_INVOICE);
        }
    }

    saveAttachments() {
        if (!this.validated) {
            return;
        }
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

    openFilePicker() {
        this.fileInput?.click();
    }

    async onFileSelected() {
        await this.processFile(this.fileInput.files?.[0]);
        // Reset so selecting the same file again still fires the change event.
        this.fileInput.value = '';
    }

    async dragDrop(e: DragEvent) {
        e.preventDefault();
        this.isDraggingFile = false;
        await this.processFile(e.dataTransfer.files?.[0]);
    }

    private async processFile(file: File | undefined) {
        if (!file) return;
        if (file.size > (3 * (1024 ** 2))) {
            this.ea.publish('alert', {alertType: AlertType.Warning, text: this.i18n.tr('alert.attachment.size-too-large-3mb')});
            return;
        }
        const totalSize = this.additionalDocumentReference.filter(item => item.Attachment?.EmbeddedDocumentBinaryObject)
            .map(item => item.Attachment.EmbeddedDocumentBinaryObject.value.length )
            .reduce((sum, value) => sum + value, 0)
            + ((file.size * 4) / 3);
        if (totalSize > (10 * (1024 ** 2))) {
            this.ea.publish('alert', {alertType: AlertType.Warning, text: this.i18n.tr('alert.upload.size-too-large')});
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
            this.ea.publish('alert', {alertType: AlertType.Danger, text: this.i18n.tr('alert.upload.type-unsupported')});
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
        } catch(error) {
            console.log(error);
            this.ea.publish('alert', {alertType: AlertType.Danger, text: this.i18n.tr('alert.upload.upload-error')});
        }
    }

    toBase64 = (file: File): Promise<string> => new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.readAsDataURL(file);
        reader.onload = () => resolve(reader.result as string);
        reader.onerror = reject;
    });

    onKeyDown(event: KeyboardEvent) {
        onModalEnter(event, () => this.saveAttachments());
    }
}
