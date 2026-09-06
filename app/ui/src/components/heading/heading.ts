import {resolve} from "@aurelia/kernel";
import {Router} from "@aurelia/router";
import {IEventAggregator} from "aurelia";
import {ThemeService} from '../../services/app/theme-service';
import {LoginService} from "../../services/app/login-service";
import {InvoiceContext} from "../../invoice/invoice-context";
import {OwnershipService, OwnershipSummary} from "../../services/app/ownership-service";
import {AlertType} from "../alert/alert";

export class Heading {
    private loginService = resolve(LoginService);
    private invoiceContext = resolve(InvoiceContext);
    private router = resolve(Router);
    private theme = resolve(ThemeService);
    private readonly ownershipService = resolve(OwnershipService);
    private readonly ea = resolve(IEventAggregator);
    ownerships: OwnershipSummary[] = [];
    selectedOwnershipKey = '';
    swapping = false;
    canAddOwnership = false;

    async attached() {
        // Tokens are held in memory, so after a page reload the access token is only restored once
        // the silent re-authorization finishes; wait for it before reading the acting ownership.
        await this.loginService.ensureAuthenticated();
        await this.ownershipService.loadOwnerships();
        await this.refreshOwnerships();
    }

    logout() {
        this.ownerships = [];
        this.selectedOwnershipKey = '';
        this.loginService.logout();
    }

    clearInvoice() {
        this.invoiceContext.clearSelectedInvoice();
    }

    async refreshOwnerships() {
        this.ownerships = this.ownershipService.getCachedOwnerships();
        const currentOwnershipKey = this.ownershipService.getCurrentOwnershipKey() ?? '';
        this.selectedOwnershipKey = currentOwnershipKey;
        this.canAddOwnership = this.ownershipService.getCurrentOwnershipType() === 'ADMIN';
        if (currentOwnershipKey && this.ownerships.some(ownership => this.getOwnershipKey(ownership) === currentOwnershipKey)) {
            queueMicrotask(() => {
                this.selectedOwnershipKey = currentOwnershipKey;
            });
            setTimeout(() => {
                this.selectedOwnershipKey = currentOwnershipKey;
            }, 0);
        }
    }

    getOwnershipLabel(ownership: OwnershipSummary) {
        return `${ownership.companyName} - ${ownership.type}`;
    }

    getOwnershipKey(ownership: OwnershipSummary) {
        return this.ownershipService.getOwnershipKey(ownership.peppolId, ownership.type);
    }

    get dashboardPath() { return this.loginService.getCurrentOwnershipRoute('/dashboard'); }
    get invoicesPath() { return this.loginService.getCurrentOwnershipRoute('/invoices'); }
    get partnersPath() { return this.loginService.getCurrentOwnershipRoute('/partners'); }
    get productsPath() { return this.loginService.getCurrentOwnershipRoute('/products'); }
    get accountPath() { return this.loginService.getCurrentOwnershipRoute('/account'); }

    async changeOwnership() {
        if (this.swapping) {
            return;
        }
        const nextOwnership = this.ownerships.find(ownership => this.getOwnershipKey(ownership) === this.selectedOwnershipKey);
        if (!nextOwnership) {
            return;
        }
        const currentOwnershipKey = this.ownershipService.getCurrentOwnershipKey();
        if (currentOwnershipKey === this.selectedOwnershipKey) {
            return;
        }
        this.swapping = true;
        try {
            await this.loginService.swapOwnership(nextOwnership);
            this.selectedOwnershipKey = this.ownershipService.getCurrentOwnershipKey() ?? '';
            this.invoiceContext.clearAccountCache();
            this.ea.publish('account:switched');
            await this.router.load(this.loginService.getCurrentOwnershipRoute('/dashboard'));
        } catch (error) {
            console.error(error);
            this.selectedOwnershipKey = currentOwnershipKey ?? '';
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to switch ownership"});
        } finally {
            this.swapping = false;
        }
    }

    async goToAddOwnership() {
        this.clearInvoice();
        await this.router.load(this.loginService.getCurrentOwnershipRoute('/add-ownership'));
    }
}
