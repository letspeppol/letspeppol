import {ProductCategoryDto, ProductCategoryService} from "../services/app/product-category-service";
import {resolve} from "@aurelia/kernel";
import {ProductContext} from "./product-context";
import {ISignaler} from "aurelia";

export class ProductCategoryModal {
    private readonly signaler = resolve(ISignaler);
    private productCategoryService = resolve(ProductCategoryService);
    private productContext = resolve(ProductContext);
    open = false;
    productCategoryToUpdate: ProductCategoryDto | undefined = undefined;
    category: ProductCategoryDto

    showModal(productCategory: ProductCategoryDto | undefined) {
        this.productCategoryToUpdate = productCategory;
        if (productCategory) {
            this.category = JSON.parse(JSON.stringify(productCategory));
        } else {
            this.category = { name: "" };
        }
        this.open = true;
    }

    closeModal() {
        this.open = false;
        this.productCategoryToUpdate = undefined;
    }

    async saveProductCategory() {
        if (this.productCategoryToUpdate) {
            const productCategory = await this.productCategoryService.updateProductCategory(this.category.id, this.category);
            this.productCategoryToUpdate.name = productCategory.name;
            this.productCategoryToUpdate.color = productCategory.color;
        } else {
            const productCategory = await this.productCategoryService.createProductCategory(this.category);
            this.productContext.addProductCategory(productCategory);
        }
        this.open = false;
        this.signaler.dispatchSignal('productUpdate');
    }

    async deleteProductCategory() {
        await this.productCategoryService.deleteProductCategory(this.category.id);
        this.productContext.deleteProductCategory(this.category);
        this.open = false;
        this.signaler.dispatchSignal('productUpdate');
    }
}
