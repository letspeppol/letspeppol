import {ValidationResultDto} from "../../../services/app/invoice-service";

export class ValidationResultModal {
    open = false;
    validationResult: ValidationResultDto;

    showModal(validationResult: ValidationResultDto) {
        this.validationResult = validationResult;
        this.open = true;
    }

    closeModal() {
        this.open = false;
    }

}