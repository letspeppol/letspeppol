import {valueConverter} from "aurelia";
import {ProductDto} from "../../services/app/product-service";
import {ProductCategoryDto} from "../../services/app/product-category-service";

@valueConverter('productCount')
export class ProductCountConverter {
    toView(category: ProductCategoryDto, products: ProductDto[]) {
        if (!products) {
            return 0;
        }
        return products.reduce((count, product) => parseInt(product.categoryId) === category.id ? count + 1 : count, 0);
    }
}
