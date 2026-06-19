import {resolve} from "@aurelia/kernel";
import {bindable, IEventAggregator,} from "aurelia";
import {ProductDto, ProductService} from "../services/app/product-service";
import {ProductContext} from "./product-context";
import {AlertType} from "../components/alert/alert";
import {ProductCategoryDto, ProductCategoryService} from "../services/app/product-category-service";
import {ProductCategoryModal} from "./product-category-modal";
import {I18N} from "@aurelia/i18n";

type SortDirection = "asc" | "desc";

export class ProductOverview {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private productContext = resolve(ProductContext);
    private productService = resolve(ProductService);
    private productCategoryService = resolve(ProductCategoryService);
    private readonly i18n = resolve(I18N);
    @bindable productCategoryModal: ProductCategoryModal;
    searchQuery = '';
    activeSortProperty = 'name';
    activeSortDirection: SortDirection = 'asc';
    pageSize = 20;
    currentPage = 0;

    attached() {
        this.loadProductsAndCategories();
    }

    async loadProductsAndCategories() {
        this.productContext.productCategories = await this.productCategoryService.getProductCategories();
        this.productContext.productCategories.forEach(value => {
            this.productContext.productCategoryMap.set(value.id, value);
        });
        this.productContext.products = await this.productService.getProducts();
    }

    get visibleProducts() {
        const start = this.currentPage * this.pageSize;
        return this.sortedFilteredProducts.slice(start, start + this.pageSize);
    }

    get visibleProductCount() {
        return this.sortedFilteredProducts.length;
    }

    get totalPages() {
        return Math.max(1, Math.ceil(this.visibleProductCount / this.pageSize));
    }

    get pageStart() {
        if (!this.visibleProductCount) {
            return 0;
        }
        return (this.currentPage * this.pageSize) + 1;
    }

    get pageEnd() {
        return Math.min((this.currentPage + 1) * this.pageSize, this.visibleProductCount);
    }

    get sortedFilteredProducts() {
        const query = this.searchQuery.toLowerCase();
        const filtered = (this.productContext.products ?? []).filter(p =>
            p.name.toLowerCase().includes(query)
            && (this.productContext.selectedProductCategory === undefined || this.productContext.selectedProductCategory.id === p.categoryId)
        );

        return [...filtered].sort((a, b) => this.compareProducts(a, b));
    }

    selectItem(product: ProductDto) {
        this.productContext.selectedProduct = product;
    }

    async deleteItem(event: Event, product: ProductDto) {
        event.stopPropagation();
        try {
            await this.productService.deleteProduct(product.id)
            this.productContext.deleteProduct(product);
            this.ea.publish('alert', {alertType: AlertType.Success, text: this.i18n.tr('alert.product.deleted')});
        } catch (e) {
            console.log(e);
            this.ea.publish('alert', {alertType: AlertType.Danger, text: this.i18n.tr('alert.product.delete-failed')});
        }
        return false;
    }

    filterItems(category: ProductCategoryDto | undefined) {
        this.productContext.selectedProductCategory = category;
        this.currentPage = 0;
    }

    toggleSort(property: string) {
        if (this.activeSortProperty === property) {
            this.activeSortDirection = this.activeSortDirection === 'asc' ? 'desc' : 'asc';
        } else {
            this.activeSortProperty = property;
            this.activeSortDirection = 'asc';
        }
        this.currentPage = 0;
    }

    searchQueryChanged() {
        this.currentPage = 0;
    }

    nextPage() {
        if (this.currentPage >= this.totalPages - 1) {
            return;
        }
        this.currentPage++;
    }

    previousPage() {
        if (this.currentPage <= 0) {
            return;
        }
        this.currentPage--;
    }

    newProductCategory() {
        this.productCategoryModal.showModal(undefined);
    }

    editProductCategory(category: ProductCategoryDto) {
        this.productCategoryModal.showModal(category);
    }

    asNum(item ) {
        if (!item) {
            return item;
        }
        return parseInt(item);
    }

    private compareProducts(a: ProductDto, b: ProductDto) {
        const left = this.productValue(a, this.activeSortProperty);
        const right = this.productValue(b, this.activeSortProperty);
        const direction = this.activeSortDirection === 'asc' ? 1 : -1;

        if (typeof left === 'number' && typeof right === 'number') {
            return (left - right) * direction;
        }

        return String(left ?? '').localeCompare(String(right ?? ''), undefined, {numeric: true, sensitivity: 'base'}) * direction;
    }

    private productValue(product: ProductDto, property: string) {
        switch (property) {
            case 'name':
                return product.name;
            case 'description':
                return product.description;
            case 'category':
                return this.productContext.productCategoryMap.get(this.asNum(product.categoryId))?.name;
            case 'reference':
                return product.reference;
            case 'barcode':
                return product.barcode;
            case 'costPrice':
                return product.costPrice ?? 0;
            case 'salePrice':
                return product.salePrice ?? 0;
            case 'taxPercentage':
                return product.taxPercentage ?? 0;
            case 'stockQuantity':
                return product.stockQuantity ?? 0;
            default:
                return '';
        }
    }
}
