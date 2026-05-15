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
        await this.refreshOwnerships();
    }

    logout() {
        this.ownerships = [];
        this.selectedOwnershipKey = '';
        this.loginService.logout();
        this.router.load('login');
    }

    clearInvoice() {
        this.invoiceContext.clearSelectedInvoice();
    }

    async refreshOwnerships() {
        this.ownerships = this.ownershipService.getCachedOwnerships();
        this.selectedOwnershipKey = this.ownershipService.getCurrentOwnershipKey() ?? '';
        this.canAddOwnership = this.ownershipService.getCurrentOwnershipType() === 'ADMIN';
    }

    getOwnershipLabel(ownership: OwnershipSummary) {
        return `${ownership.companyName} - ${ownership.type}`;
    }

    getOwnershipKey(ownership: OwnershipSummary) {
        return this.ownershipService.getOwnershipKey(ownership.peppolId, ownership.type);
    }

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
            await this.ownershipService.swapOwnership(nextOwnership);
            this.selectedOwnershipKey = this.ownershipService.getCurrentOwnershipKey() ?? '';
            this.clearInvoice();
            await this.router.load('dashboard');
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
        await this.router.load('/add-ownership');
    }
}
