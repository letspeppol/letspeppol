import {PartnerDto, PartnerService} from "../../../services/app/partner-service";
import {resolve} from "@aurelia/kernel";
import {bindable} from "aurelia";

export class CustomerSearch {
    private partnerService = resolve(PartnerService);
    searchQuery = '';
    customers: PartnerDto[] = [];
    filteredCustomers: PartnerDto[] = [];
    showSuggestions = false;
    highlightedIndex = -1;
    customerSearchInput: HTMLInputElement; // ref from template
    @bindable selectCustomerFunction: (c: PartnerDto) => void;

    attached() {
        this.getPartners();
    }

    async getPartners() {
        try {
            const response = await this.partnerService.getPartners();
            this.customers = response.filter((partner: PartnerDto) => partner.customer);
        } catch (e) {
            console.warn('Partner API failed, using mock data');
        }
    }

    onSearchInput() {
        const q = this.searchQuery?.trim().toLowerCase();
        if (!q) {
            this.filteredCustomers = [];
            this.showSuggestions = false;
            this.highlightedIndex = -1;
            return;
        }
        this.filteredCustomers = this.customers.filter(c =>
            (c.name && c.name.toLowerCase().includes(q)) ||
            (c.companyNumber && c.companyNumber.toLowerCase().includes(q))
        ).slice(0, 8);
        this.showSuggestions = this.filteredCustomers.length > 0;
        this.highlightedIndex = this.filteredCustomers.length ? 0 : -1;
    }

    onSearchKeydown(e: KeyboardEvent) {
        if (!this.showSuggestions) return;
        if (e.key === 'ArrowDown') {
            e.preventDefault();
            this.highlightedIndex = (this.highlightedIndex + 1) % this.filteredCustomers.length;
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            this.highlightedIndex = (this.highlightedIndex - 1 + this.filteredCustomers.length) % this.filteredCustomers.length;
        } else if (e.key === 'Enter') {
            if (this.highlightedIndex >= 0) {
                e.preventDefault();
                this.selectCustomer(this.filteredCustomers[this.highlightedIndex]);
            }
        } else if (e.key === 'Escape') {
            this.showSuggestions = false;
        }
    }

    onSearchBlur() {
        setTimeout(() => {
            this.showSuggestions = false;
        }, 120);
    }

    selectCustomer(c: PartnerDto) {
        if (this.selectCustomerFunction) {
            this.selectCustomerFunction(c);
        }
        this.searchQuery = `${c.name} (${c.companyNumber})`;
        this.showSuggestions = false;
    }

    public resetSearch() {
        this.searchQuery = '';
        this.filteredCustomers = [];
        this.showSuggestions = false;
        this.highlightedIndex = -1;
    }

    public focusInput() {
        // Use microtask to ensure element is in DOM & visible
        queueMicrotask(() => this.customerSearchInput?.focus());
    }
}
