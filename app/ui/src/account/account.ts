import {CompanyService} from "../services/company-service";
import {resolve} from "@aurelia/kernel";

export class Account {
    companyService = resolve(CompanyService);
    company;
    constructor() {
        this.company = this.companyService.myCompany;
    }
}