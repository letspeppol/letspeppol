package io.tubs.app.config;

import io.tubs.app.dto.*;
import io.tubs.app.repository.CompanyRepository;
import io.tubs.app.service.CompanyService;
import io.tubs.app.service.PartnerService;
import io.tubs.app.service.ProductCategoryService;
import io.tubs.app.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@RequiredArgsConstructor
@Component
public class DataInitializer implements CommandLineRunner {

    private final CompanyRepository companyRepository;
    private final CompanyService companyService;
    private final PartnerService partnerService;
    private final ProductService productService;
    private final ProductCategoryService productCategoryService;

    @Override
    @Transactional
    public void run(String... args) {
        String companyNumber = "0705969661";
        if (companyRepository.findByCompanyNumber(companyNumber).isEmpty()) {
            RegistrationRequest registrationRequest = new RegistrationRequest(
                    companyNumber,
                    "Digita bv.",
                    "Demerstraat",
                    "2",
                    "Hasselt",
                    "3500",
                    "Michiel Wouters",
                    "letspeppol@itaa.be"
            );
            companyService.register(registrationRequest);
            PartnerDto partner = new PartnerDto(
                    null,
                    "BE1023290711",
                    "John Doe",
                    "john@doe.com",
                    "0208:1023290711",
                    true,
                    false,
                    "Last day 30 days",
                    "BE1023290711",
                    "1000",
                    new AddressDto(null,"Bree", "3960", "Kerkstraat", "15", "BE")
            );
            partnerService.createPartner(companyNumber, partner);
            PartnerDto partner2 = new PartnerDto(
                    null,
                    "BE0202239951",
                    "Jane Smith",
                    "jane@smith.com",
                    "0208:0202239951",
                    true,
                    false,
                    "First day 60 days",
                    "BE876543215",
                    "2000",
                    new AddressDto(null,"Genk", "3600", "Stationsstraat", "22", "BE")
            );
            partnerService.createPartner(companyNumber, partner2);
            ProductCategoryDto productCategory = new ProductCategoryDto(null, "Clothes", "#feeffe", null, null);
            productCategory = productCategoryService.createCategory(companyNumber, productCategory);
            ProductDto product = new ProductDto(
                    null,
                    "T-shirt",
                    "AB T-Shirt size L",
                    "465AZ98894",
                    null,
                    new BigDecimal("6.99"),
                    new BigDecimal("14.99"),
                    new BigDecimal("21"),
                    productCategory.id()
            );
            productService.createProduct(companyNumber, product);

        }
        companyNumber = "1023290711";
        if (companyRepository.findByCompanyNumber(companyNumber).isEmpty()) {
            RegistrationRequest registrationRequest = new RegistrationRequest(
                    companyNumber,
                    "SoftwareOplossing bv.",
                    "Demerstraat",
                    "2",
                    "Hasselt",
                    "3500",
                    "Bart In stukken",
                    "bart@softwareoplossing.be"
            );
            companyService.register(registrationRequest);
        }
    }
}
