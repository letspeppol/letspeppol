import {resolve} from '@aurelia/kernel';
import {I18N} from '@aurelia/i18n';

interface FaqItem {
    id: string;
    category: string;
}

export class Help {
    private readonly i18n = resolve(I18N);
    searchQuery = '';
    selectedCategory = 'all';

    readonly categories = ['all', 'getting-started', 'invoices', 'receiving', 'contacts', 'vat', 'files', 'account', 'troubleshooting'];
    readonly items: FaqItem[] = [
        {id: 'what-is-peppol', category: 'getting-started'},
        {id: 'is-it-free', category: 'getting-started'},
        {id: 'first-invoice', category: 'getting-started'},
        {id: 'create-send', category: 'invoices'},
        {id: 'invoice-status', category: 'invoices'},
        {id: 'correct-invoice', category: 'invoices'},
        {id: 'receive', category: 'receiving'},
        {id: 'sender-not-found', category: 'receiving'},
        {id: 'customer-peppol', category: 'contacts'},
        {id: 'products', category: 'contacts'},
        {id: 'vat-exemption', category: 'vat'},
        {id: 'structured-message', category: 'vat'},
        {id: 'attachments', category: 'files'},
        {id: 'ubl-upload', category: 'files'},
        {id: 'validation', category: 'files'},
        {id: 'eid-every-time', category: 'account'},
        {id: 'switch-provider', category: 'account'},
        {id: 'data-security', category: 'account'},
        {id: 'languages', category: 'account'},
        {id: 'login', category: 'troubleshooting'},
        {id: 'eid-fails', category: 'troubleshooting'},
        {id: 'contact-support', category: 'troubleshooting'},
    ];

    get filteredItems(): FaqItem[] {
        const query = this.searchQuery.trim().toLocaleLowerCase();
        return this.items.filter((item) => {
            if (this.selectedCategory !== 'all' && item.category !== this.selectedCategory) return false;
            if (!query) return true;
            const question = this.i18n.tr(`help.questions.${item.id}.question`);
            const answer = this.i18n.tr(`help.questions.${item.id}.answer`);
            return `${question} ${answer}`.toLocaleLowerCase().includes(query);
        });
    }

    selectCategory(category: string) {
        this.selectedCategory = category;
    }

    clearSearch() {
        this.searchQuery = '';
        this.selectedCategory = 'all';
    }
}
