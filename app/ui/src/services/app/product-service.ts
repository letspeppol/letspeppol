import {singleton} from "aurelia";
import {resolve} from "@aurelia/kernel";
import {AppApi} from "./app-api";

export interface ProductDto {
    id?: number,
    name: string,
    description?: string,
    reference?: string,
    barcode?: string,
    costPrice?: number,
    salePrice?: number,
    taxPercentage?: number,
    stockQuantity?: number
    categoryId?: number,
}

@singleton()
export class ProductService {
    private appApi = resolve(AppApi);

    async getProducts() : Promise<ProductDto[]> {
        return await this.appApi.httpClient.get('/api/product').then(response => response.json());
    }

    async createProduct(product: ProductDto) : Promise<ProductDto> {
        return await this.appApi.httpClient.post('/api/product', JSON.stringify(product)).then(response => response.json());
    }

    async updateProduct(id: number, product: ProductDto) : Promise<ProductDto> {
        return await this.appApi.httpClient.put(`/api/product/${id}`, JSON.stringify(product)).then(response => response.json());
    }

    async deleteProduct(id:number) {
        return await this.appApi.httpClient.delete(`/api/product/${id}`);
    }
}
