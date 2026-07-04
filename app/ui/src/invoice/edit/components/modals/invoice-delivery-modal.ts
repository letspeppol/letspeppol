import {DocumentDetailsDto} from "../../../../services/app/invoice-service";

export class InvoiceDeliveryModal {
    open = false;
    details: DocumentDetailsDto;

    showModal(details: DocumentDetailsDto) {
        this.details = details;
        this.open = true;
    }

    closeModal() {
        this.open = false;
    }

    get partnerMessageOnLabel(): string {
        return this.formatDateTime(this.details?.partnerPeppolMessageOn);
    }

    get processedOnLabel(): string {
        return this.formatDateTime(this.details?.processedOn);
    }

    private formatDateTime(value?: string): string {
        if (!value) {
            return "/";
        }
        return new Intl.DateTimeFormat(undefined, {
            dateStyle: "medium",
            timeStyle: "short"
        }).format(new Date(value));
    }
}
