import {DirectionTotals, StatisticsService, Totals} from "../services/app/statistics-service";
import {resolve} from "@aurelia/kernel";
import {IVatDisplay, VatDisplayMode} from "../services/app/vat-display-service";

const EMPTY_TOTALS: DirectionTotals = {
    payableOpen: 0, payableOverdue: 0, payableThisYear: 0,
    receivableOpen: 0, receivableOverdue: 0, receivableThisYear: 0,
};

export class Dashboard {
    private statisticsService = resolve(StatisticsService);
    private vatDisplay = resolve(IVatDisplay);
    totals: Totals;
    vatMode: VatDisplayMode = this.vatDisplay.mode;
    activeTotals: DirectionTotals = EMPTY_TOTALS;
    private vatUnsubscribe: () => void;

    attached() {
        this.vatUnsubscribe = this.vatDisplay.subscribe(mode => {
            this.vatMode = mode;
            this.refreshActive();
        });
        this.loadTotals();
    }

    detaching() {
        this.vatUnsubscribe?.();
    }

    loadTotals() {
        this.statisticsService.getTotals().then(response => {
            this.totals = response;
            this.refreshActive();
        });
    }

    private refreshActive() {
        if (!this.totals) {
            this.activeTotals = EMPTY_TOTALS;
            return;
        }
        this.activeTotals = this.vatMode === 'incl' ? this.totals.inclVat : this.totals.exclVat;
    }
}
