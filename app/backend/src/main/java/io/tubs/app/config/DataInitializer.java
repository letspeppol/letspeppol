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
                    "michiel@digita.be"
            );
            companyService.register(registrationRequest);
            PartnerDto partner = new PartnerDto(
                    null,
                    "BE123456789",
                    "John Doe",
                    "john@doe.com",
                    true,
                    false,
                    "Last day 30 days",
                    "BE12345678900",
                    "1000",
                    new AddressDto(null,"Bree", "3960", "Kerkstraat", "15")
            );
            partnerService.createPartner(companyNumber, partner);
            PartnerDto partner2 = new PartnerDto(
                    null,
                    "BE987654321",
                    "Jane Smith",
                    "jane@smith.com",
                    true,
                    false,
                    "First day 60 days",
                    "BE98765432100",
                    "2000",
                    new AddressDto(null,"Genk", "3600", "Stationsstraat", "22")
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
