import {ProxyService, Totals} from "../services/proxy/proxy-service";
import {resolve} from "@aurelia/kernel";

export class Dashboard {
    private proxyService = resolve(ProxyService);
    totals: Totals;

    attached() {
        this.loadTotals();
    }

    loadTotals() {
        this.proxyService.getTotals().then(response => this.totals = response);
    }
}