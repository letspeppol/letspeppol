import {bindable} from "aurelia";

export interface PasswordRuleState {
    lengthOk: boolean;
    lowerOk: boolean;
    upperOk: boolean;
    numberOk: boolean;
    symbolOk: boolean;
    matchOk: boolean;
    pwScore: number;
    pwStrong: boolean;
}

export class ChoosePassword {
    @bindable password = '';
    @bindable confirmPassword = '';
    @bindable showHeading = false;

    get rules(): PasswordRuleState {
        const value = this.password ?? '';
        const confirmation = this.confirmPassword ?? '';
        const lengthOk = value.length >= 12;
        const lowerOk = /[a-z]/.test(value);
        const upperOk = /[A-Z]/.test(value);
        const numberOk = /\d/.test(value);
        const symbolOk = /[^A-Za-z0-9]/.test(value);
        const matchOk = !!value && value === confirmation;
        const pwScore = value
            ? Math.min(4, Math.floor(value.length / 4))
                + (lowerOk ? 1 : 0)
                + (upperOk ? 1 : 0)
                + (numberOk ? 1 : 0)
                + (symbolOk ? 1 : 0)
            : 0;

        return {
            lengthOk,
            lowerOk,
            upperOk,
            numberOk,
            symbolOk,
            matchOk,
            pwScore,
            pwStrong: lengthOk && lowerOk && upperOk && numberOk && symbolOk,
        };
    }
}
