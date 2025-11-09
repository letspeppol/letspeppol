import {AlertType} from "../components/alert/alert";
import {IEventAggregator, ISignaler} from "aurelia";
import {resolve} from "@aurelia/kernel";
import {ProductService} from "../services/app/product-service";
import {ProductContext} from "./product-context";

export class ProductEdit {
    private readonly signaler = resolve(ISignaler);
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private readonly productService = resolve(ProductService);
    private readonly productContext = resolve(ProductContext);
    taxPercentages: number[] = [21, 12, 6, 0];

    categoryIdMatcher = (a: number, b: number) => { return a == b; };

    async saveProduct() {
        try {
            let successMessage = "Product updated successfully";
            if (this.productContext.selectedProduct.id) {
                await this.productService.updateProduct(this.productContext.selectedProduct.id, this.productContext.selectedProduct);
            } else {
                const product = await this.productService.createProduct(this.productContext.selectedProduct);
                this.productContext.addProduct(product);
                successMessage = "Product created successfully";
            }
            this.ea.publish('alert', {alertType: AlertType.Success, text: successMessage});
            this.productContext.selectedProduct = undefined;
            this.signaler.dispatchSignal('productUpdate');
        } catch(e) {
            console.error(e);
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to update account"});
        }
    }

}
