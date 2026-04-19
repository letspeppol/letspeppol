import {resolve} from "@aurelia/kernel";
import {InvoiceService, ValidationResultDto} from "../../../services/app/invoice-service";
import {AlertType} from "../../../components/alert/alert";
import {IEventAggregator} from "aurelia";
import {toErrorResponse} from "../../../app/util/error-response-handler";
import {InvoiceContext} from "../../invoice-context";


export class UploadUblModal {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private invoiceService = resolve(InvoiceService);
    private invoiceContext = resolve(InvoiceContext);

    open = false;
    isDraggingFile = false;
    uploadUblWrapper: HTMLElement;
    validationResult: ValidationResultDto;

    showModal() {
        this.validationResult = undefined;
        this.open = true;
    }

    closeModal() {
        this.open = false;
    }

    retry() {
        this.validationResult = undefined;
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
        if (!this.uploadUblWrapper.contains(event.relatedTarget as Node)) {
            this.isDraggingFile = false;
        }
    }

    async dragDrop(e: DragEvent) {
        e.preventDefault();
        this.isDraggingFile = false;

        const file = e.dataTransfer.files[0];
        if (file.size > (10 * (1024 ** 2))) {
            this.ea.publish('alert', {alertType: AlertType.Warning, text: "Sum of all file sizes can not be more than 10MB"});
            return;
        }

        const allowedTypes: string[] = ['application/xml', 'text/xml'];
        if (!allowedTypes.includes(file.type as string)) {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "File type not supported"});
            return;
        }
        const xml: string = await this.readXmlAsString(file);

        this.ea.publish('showOverlay', "Validating");
        try {
            const response = await this.invoiceService.validate(xml);
            if (!response.isValid) {
                this.validationResult = response;
                return;
            }
        } finally {
            this.ea.publish('hideOverlay');
        }

        try {
            this.ea.publish('showOverlay', "Creating draft");
            const documentDraftDto = await this.invoiceService.createDocument(xml, true, true);
            this.invoiceContext.draftPage.content.unshift(documentDraftDto);
            this.invoiceContext.draftPage.totalElements++;
            this.ea.publish('alert', {alertType: AlertType.Success, text: "Invoice draft created successfully"});
            this.closeModal();
        } catch (e: unknown) {
            const errorResponse = await toErrorResponse(e);
            if (errorResponse?.errorCode === 'INVOICE_NUMBER_ALREADY_USED') {
                this.ea.publish('alert', { alertType: AlertType.Danger, text: 'Invoice number already used' });
            } else if (errorResponse?.message) {
                this.ea.publish('alert', { alertType: AlertType.Danger, text: errorResponse.message });
            } else {
                this.ea.publish('alert', { alertType: AlertType.Danger, text: 'Failed to send invoice' });
            }
        } finally {
            this.ea.publish('hideOverlay');
        }

    }

    readXmlAsString = (file: File): Promise<string> => {
        return new Promise((resolve, reject) => {
            const reader = new FileReader();

            reader.readAsText(file);

            reader.onload = () => resolve(reader.result as string);
            reader.onerror = (error) => reject(error);
        });
    };

}
