import {ProductDto, ProductService} from "../../../services/app/product-service";
import {resolve} from "@aurelia/kernel";
import {bindable} from "aurelia";
import {ProductContext} from "../../../product/product-context";
import {PriceConverter} from "../../../app/value-converters/price";

export class ProductNameSearch {
    private productService = resolve(ProductService);
    private productContext = resolve(ProductContext);
    @bindable inputValue = '';
    @bindable readonly;
    @bindable label: string;
    filteredProducts: ProductDto[] = [];
    showSuggestions = false;
    highlightedIndex = -1;
    productSearchInput: HTMLInputElement; // ref from template
    @bindable selectProductFunction: (c: ProductDto) => void;
    @bindable onSearchChangeFunction: (e: UIEvent) => void;
    private readonly priceConverter = new PriceConverter();
    top = '';
    left = '';
    width= '';

    priceLabel(product: ProductDto): string {
        return this.priceConverter.toView(product.salePrice);
    }

    attached() {
        this.getProducts();
    }

    async getProducts() {
        if (!this.productContext.products) {
            try {
                this.productContext.products = await this.productService.getProducts()
            } catch {
                console.warn('Product API failed');
            }
        }
    }

    onSearchChange(e: UIEvent) {
        if (this.onSearchChangeFunction) {
            this.onSearchChangeFunction(e);
        }
    }

    onSearchInput(e: KeyboardEvent) {
        const q = this.inputValue?.trim().toLowerCase();
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
                this.top = `${rect.bottom}px`;
                this.left = `${rect.left}px`;
                this.width = `${rect.width}px`;
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
        this.inputValue = `${c.name}`;
        this.showSuggestions = false;
    }

    public resetSearch() {
        this.inputValue = '';
        this.filteredProducts = [];
        this.showSuggestions = false;
        this.highlightedIndex = -1;
    }

    public focusInput() {
        // Use microtask to ensure element is in DOM & visible
        queueMicrotask(() => this.productSearchInput?.focus());
    }
}
