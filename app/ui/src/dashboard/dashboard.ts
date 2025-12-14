import {StatisticsService, Totals} from "../services/app/statistics-service";
import {resolve} from "@aurelia/kernel";

export class Dashboard {
    private statisticsService = resolve(StatisticsService);
    totals: Totals;

    attached() {
        this.loadTotals();
    }

    loadTotals() {
        this.statisticsService.getTotals().then(response => this.totals = response);
    }
}