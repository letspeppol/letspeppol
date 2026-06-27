import {DirectionTotals, StatisticsService, Totals} from "../services/app/statistics-service";
import {resolve} from "@aurelia/kernel";
import {CompanyService} from "../services/app/company-service";
import {getVatDisplayMode, IVatDisplay, VatDisplayMode} from "../services/app/vat-display-service";

const EMPTY_TOTALS: DirectionTotals = {
    payableOpen: 0, payableOverdue: 0, payableThisYear: 0,
    receivableOpen: 0, receivableOverdue: 0, receivableThisYear: 0,
};

export class Dashboard {
    private statisticsService = resolve(StatisticsService);
    private companyService = resolve(CompanyService);
    private vatDisplay = resolve(IVatDisplay);
    totals: Totals;
    vatMode: VatDisplayMode = getVatDisplayMode(this.companyService.myCompany?.vatNumber, this.vatDisplay.mode);
    activeTotals: DirectionTotals = EMPTY_TOTALS;
    private unsubscribeVatDisplay?: () => void;

    attached() {
        this.unsubscribeVatDisplay = this.vatDisplay.subscribe(mode => {
            this.vatMode = getVatDisplayMode(this.companyService.myCompany?.vatNumber, mode);
            this.refreshActive();
        });
        void this.load();
    }

    detaching() {
        this.unsubscribeVatDisplay?.();
    }

    private async load() {
        await Promise.all([
            this.loadCompany(),
            this.loadTotals(),
        ]);
    }

    private async loadCompany() {
        if (!this.companyService.myCompany) {
            await this.companyService.getAndSetMyCompanyForToken().catch(() => undefined);
        }
        this.vatMode = getVatDisplayMode(this.companyService.myCompany?.vatNumber, this.vatDisplay.mode);
        this.refreshActive();
    }

    private async loadTotals() {
        this.totals = await this.statisticsService.getTotals();
        this.refreshActive();
    }

    private refreshActive() {
        if (!this.totals) {
            this.activeTotals = EMPTY_TOTALS;
            return;
        }
        this.activeTotals = this.vatMode === 'incl' ? this.totals.inclVat : this.totals.exclVat;
    }
}
