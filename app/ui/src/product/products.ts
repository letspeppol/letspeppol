import {resolve} from "@aurelia/kernel";
import {ProductContext} from "./product-context";

export class Products {
    private productContext = resolve(ProductContext);

}
