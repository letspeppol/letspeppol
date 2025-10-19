import {ProductDto, ProductService} from "../../../services/app/product-service";
import {resolve} from "@aurelia/kernel";
import {bindable} from "aurelia";
import {ProductContext} from "../../../product/product-context";

export class ProductNameSearch {
    private productService = resolve(ProductService);
    private productContext = resolve(ProductContext);
    searchQuery = '';
    filteredProducts: ProductDto[] = [];
    showSuggestions = false;
    highlightedIndex = -1;
    productSearchInput: HTMLInputElement; // ref from template
    @bindable selectProductFunction: (c: ProductDto) => void;
    top = '';
    left = '';
    width= '';

    attached() {
        this.getProducts();
    }

    async getProducts() {
        if (!this.productContext.products) {
            try {
                this.productContext.products = await this.productService.getProducts()
            } catch (e) {
                console.warn('Product API failed, using mock data');
            }
        }
    }

    onSearchInput(e: KeyboardEvent) {
        const q = this.searchQuery?.trim().toLowerCase();
        if (!q) {
            this.filteredProducts = [];
            this.showSuggestions = false;
            this.highlightedIndex = -1;
            return;
        }
        this.filteredProducts = this.productContext.products.filter(c =>
            (c.name && c.name.toLowerCase().includes(q))
        ).slice(0, 8);


        if (this.filteredProducts.length > 0) {
            const input =  e.target as (HTMLInputElement);
            setTimeout(() => {
                const rect = input.getBoundingClientRect();
                this.top = `${rect.bottom + window.scrollY}px`;
                this.left = `${rect.left + window.scrollX}px`;
                this.width = `${rect.width}px`;
                console.log(this.left);
                this.showSuggestions = true;
            });
        } else {
            this.showSuggestions = false;
        }
        this.highlightedIndex = this.filteredProducts.length ? 0 : -1;
    }

    onSearchKeydown(e: KeyboardEvent) {
        if (!this.showSuggestions) return;
        if (e.key === 'ArrowDown') {
            e.preventDefault();
            this.highlightedIndex = (this.highlightedIndex + 1) % this.filteredProducts.length;
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            this.highlightedIndex = (this.highlightedIndex - 1 + this.filteredProducts.length) % this.filteredProducts.length;
        } else if (e.key === 'Enter') {
            if (this.highlightedIndex >= 0) {
                e.preventDefault();
                this.selectProduct(this.filteredProducts[this.highlightedIndex]);
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

    selectProduct(c: ProductDto) {
        if (this.selectProductFunction) {
            this.selectProductFunction(c);
        }
        this.searchQuery = `${c.name}`;
        this.showSuggestions = false;
    }

    public resetSearch() {
        this.searchQuery = '';
        this.filteredProducts = [];
        this.showSuggestions = false;
        this.highlightedIndex = -1;
    }

    public focusInput() {
        // Use microtask to ensure element is in DOM & visible
        queueMicrotask(() => this.productSearchInput?.focus());
    }
}
