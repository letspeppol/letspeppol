import {resolve} from "@aurelia/kernel";
import {bindable} from "aurelia";
import {CompanySearchService} from "../../services/kyc/company-search-service";
import {KycCompanyResponse} from "../../services/kyc/registration-service";

export class CompanySearch {
    private companySearchService = resolve(CompanySearchService);
    companies: KycCompanyResponse[] = [];
    showSuggestions = false;
    highlightedIndex = -1;
    @bindable searchQuery = '';
    @bindable selectCompanyFunction: (c: KycCompanyResponse) => void;
    @bindable confirmCompanyFunction: () => void;

    attached() {
        
    }

    async getCompanies() {
        try {
            this.companies = await this.companySearchService.searchCompany({'companyName' : this.searchQuery});
        } catch (e) {
            console.warn('KYC API failed, using mock data');
        }
    }

    onSearchInput() {
        const q = this.searchQuery?.trim().toLowerCase();
        if (!q) {
            this.companies = [];
            this.showSuggestions = false;
            this.highlightedIndex = -1;
            return;
        }
    }

    async searchCompanies() {
        if (this.searchQuery.length >= 5) {
            await this.getCompanies();
            this.showSuggestions = this.companies.length > 0;
            this.highlightedIndex = this.companies.length ? 0 : -1;
        }
    }

    async onSearchKeydown(e: KeyboardEvent) {
        if (!this.showSuggestions) {
            if (e.key === 'Enter') {
                await this.searchCompanies();
                return;
            }
        }
        if (e.key === 'ArrowDown') {
            e.preventDefault();
            this.highlightedIndex = (this.highlightedIndex + 1) % this.companies.length;
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            this.highlightedIndex = (this.highlightedIndex - 1 + this.companies.length) % this.companies.length;
        } else if (e.key === 'Enter') {
            if (this.highlightedIndex >= 0) {
                e.preventDefault();
                this.selectCompany(this.companies[this.highlightedIndex]);
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

    selectCompany(c: KycCompanyResponse) {
        if (this.selectCompanyFunction) {
            this.selectCompanyFunction(c);
        }
        this.searchQuery = `${c.name}`;
        this.showSuggestions = false;
    }
}
