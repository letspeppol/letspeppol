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

    get hasPartnerPeppolAccessPoint(): boolean {
        return this.details?.partnerPeppolAccessPoint != null;
    }

    get hasProcessedStatus(): boolean {
        return this.details?.processedStatus != null;
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
        const date = new Date(value);
        const pad = (part: number) => part.toString().padStart(2, "0");
        return `${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
    }
}
