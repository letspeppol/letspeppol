package org.letspeppol.app.config;

import org.letspeppol.app.dto.*;
import org.letspeppol.app.repository.CompanyRepository;
import org.letspeppol.app.service.CompanyService;
import org.letspeppol.app.service.PartnerService;
import org.letspeppol.app.service.ProductCategoryService;
import org.letspeppol.app.service.ProductService;
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
        String peppolId = "0208:0705969661";
        if (companyRepository.findByPeppolId(peppolId).isEmpty()) {
            RegistrationRequest registrationRequest = new RegistrationRequest(
                    peppolId,
                    "BE0705969661",
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
            partnerService.createPartner(peppolId, partner);
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
            partnerService.createPartner(peppolId, partner2);
            ProductCategoryDto productCategory = new ProductCategoryDto(null, "Clothes", "#feeffe", null, null);
            productCategory = productCategoryService.createCategory(peppolId, productCategory);
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
            productService.createProduct(peppolId, product);

        }
        peppolId = "0208:1023290711";
        if (companyRepository.findByPeppolId(peppolId).isEmpty()) {
            RegistrationRequest registrationRequest = new RegistrationRequest(
                    peppolId,
                    "BE1023290711",
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
