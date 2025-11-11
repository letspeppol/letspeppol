import {valueConverter} from "aurelia";

@valueConverter('price')
export class PriceConverter {
    toView(value: number) {
        if (value == null) {
            return '';
        }
        return new Intl.NumberFormat('nl-BE', {
            style: 'currency',
            currency: 'EUR',
            currencyDisplay: 'symbol',
        }).format(value);
    }
}
