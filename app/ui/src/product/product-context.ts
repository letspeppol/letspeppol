import {bindable, singleton} from "aurelia";
import {ProductDto} from "../services/app/product-service";
import {ProductCategoryDto} from "../services/app/product-category-service";

@singleton
export class ProductContext {
    @bindable selectedProduct: ProductDto | undefined = undefined;
    selectedProductCategory: ProductCategoryDto | undefined = undefined;
    products: ProductDto[] = undefined;
    productCategories: ProductCategoryDto[] = [];
    productCategoryMap = new Map();

    newProduct() {
        this.selectedProduct = { name: "" };
    }

    clearSelectedProduct() {
        this.selectedProduct = undefined;
    }

    replaceProduct(currentProduct: ProductDto, newProduct: ProductDto) {
        let index = this.products.findIndex(item => item === currentProduct);
        if (index > -1) {
            this.products.splice(index, 1, newProduct);
        }
    }

    addProduct(product: ProductDto) {
        this.products.unshift(product);
    }

    deleteProduct(product) {
        const index = this.products.findIndex(item => item === product);
        if (index > -1) {
            this.products.splice(index, 1);
        }
    }

    addProductCategory(productCategory: ProductCategoryDto) {
        this.productCategories.push(productCategory);
        this.productCategoryMap.set(productCategory.id, productCategory);
    }

    deleteProductCategory(productCategory: ProductCategoryDto) {
        this.productCategories.splice(this.productCategories.findIndex(value => value === productCategory), 1);
        this.productCategoryMap.delete(productCategory.id);
    }
}
