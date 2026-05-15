import {StatisticsService, Totals} from "../services/app/statistics-service";
import {resolve} from "@aurelia/kernel";
import {IEventAggregator, IDisposable} from "aurelia";

export class Dashboard {
    private statisticsService = resolve(StatisticsService);
    private readonly ea = resolve(IEventAggregator);
    private sub?: IDisposable;
    totals: Totals;

    attached() {
        this.sub = this.ea.subscribe('account:switched', () => {
            this.loadTotals();
        });
        this.loadTotals();
    }

    unbinding() {
        this.sub?.dispose();
    }

    loadTotals() {
        this.statisticsService.getTotals().then(response => this.totals = response);
    }
}
