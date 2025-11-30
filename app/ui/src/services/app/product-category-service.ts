import {singleton} from "aurelia";
import {resolve} from "@aurelia/kernel";
import {AppApi} from "./app-api";

export interface ProductCategoryDto {
    id?: number,
    name: string,
    color?: string,
    parentId?: number,
    children?: ProductCategoryDto[] | undefined
}

@singleton()
export class ProductCategoryService {
    private appApi = resolve(AppApi);

    async getProductCategories() : Promise<ProductCategoryDto[]> {
        return await this.appApi.httpClient.get('/sapi/product-category').then(response => response.json());
    }

    async createProductCategory(productCategory: ProductCategoryDto) : Promise<ProductCategoryDto> {
        return await this.appApi.httpClient.post('/sapi/product-category', JSON.stringify(productCategory)).then(response => response.json());
    }

    async updateProductCategory(id: number, productCategory: ProductCategoryDto) : Promise<ProductCategoryDto> {
        return await this.appApi.httpClient.put(`/sapi/product-category/${id}`, JSON.stringify(productCategory)).then(response => response.json());
    }

    async deleteProductCategory(id:number) {
        return await this.appApi.httpClient.delete(`/sapi/product-category/${id}`);
    }
}
