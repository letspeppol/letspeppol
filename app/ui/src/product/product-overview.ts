import {resolve} from "@aurelia/kernel";
import {bindable, IEventAggregator,} from "aurelia";
import {ProductDto, ProductService} from "../services/app/product-service";
import {ProductContext} from "./product-context";
import {AlertType} from "../components/alert/alert";
import {ProductCategoryDto, ProductCategoryService} from "../services/app/product-category-service";
import {ProductCategoryModal} from "./product-category-modal";

export class ProductOverview {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private productContext = resolve(ProductContext);
    private productService = resolve(ProductService);
    private productCategoryService = resolve(ProductCategoryService);
    @bindable productCategoryModal: ProductCategoryModal;
    searchQuery = '';

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

    selectItem(product: ProductDto) {
        this.productContext.selectedProduct = product;
    }

    async deleteItem(event: Event, product: ProductDto) {
        event.stopPropagation();
        try {
            await this.productService.deleteProduct(product.id)
            this.productContext.deleteProduct(product);
            this.ea.publish('alert', {alertType: AlertType.Success, text: "Product deleted"});
        } catch (e) {
            console.log(e);
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to delete product"});
        }
        return false;
    }

    filterItems(category: ProductCategoryDto | undefined) {
        this.productContext.selectedProductCategory = category;
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
}
