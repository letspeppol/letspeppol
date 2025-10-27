import {resolve} from "@aurelia/kernel";
import {ListItemV1, ProxyService} from "../services/proxy-service";
import {InvoiceContext} from "./invoice-context";
import {parseInvoice} from "../peppol/ubl-parser";


export class InvoiceOverview {
    private letsPeppolService = resolve(ProxyService);
    private invoiceContext = resolve(InvoiceContext);
    all: ListItemV1[] = [];
    incoming: ListItemV1[] = [];
    outgoing: ListItemV1[] = [];
    activeItems: ListItemV1[] = [];
    box = 'all'

    attached() {
        this.loadInvoices();
    }

    loadInvoices() {
        const ip = this.letsPeppolService.getIncomingInvoices().then(items => this.incoming = items);
        const op = this.letsPeppolService.getOutgoingInvoices().then(items => this.outgoing = items);
        Promise.all([ip, op]).then(([incoming, outgoing]) => {
            this.all = [...incoming, ...outgoing];
            this.setActiveItems('all');
        });
    }

    setActiveItems(box) {
        this.box = box;
        switch (box) {
            case 'all':
                this.activeItems = this.all;
                break;
            case 'outgoing':
                this.activeItems = this.outgoing;
                break;
            case 'incoming':
                this.activeItems = this.incoming;
                break;
        }
    }

    selectItem(item: ListItemV1) {
        this.letsPeppolService.getDocument(item.type, item.direction, item.uuid).then((doc) => {
            console.log(parseInvoice(doc));
        });
    }
}