import {DirectionTotals, StatisticsService, Totals} from "../services/app/statistics-service";
import {resolve} from "@aurelia/kernel";
import {CompanyService} from "../services/app/company-service";
import {getAutomaticVatDisplayMode, VatDisplayMode} from "../services/app/vat-display-service";

const EMPTY_TOTALS: DirectionTotals = {
    payableOpen: 0, payableOverdue: 0, payableThisYear: 0,
    receivableOpen: 0, receivableOverdue: 0, receivableThisYear: 0,
};

export class Dashboard {
    private statisticsService = resolve(StatisticsService);
    private companyService = resolve(CompanyService);
    totals: Totals;
    vatMode: VatDisplayMode = getAutomaticVatDisplayMode(this.companyService.myCompany?.vatNumber);
    activeTotals: DirectionTotals = EMPTY_TOTALS;

    attached() {
        void this.load();
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
        this.vatMode = getAutomaticVatDisplayMode(this.companyService.myCompany?.vatNumber);
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
