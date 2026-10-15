package org.letspeppol.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class SapiPermissionCoverageTest {

    private static final String MEMBER = "hasAuthority('kyc_user')";
    private static final String ADMIN = "hasAuthority('company_admin')";
    private static final String READ = "hasAuthority('INVOICE_READ')";
    private static final String DRAFT = "hasAuthority('INVOICE_DRAFT')";
    private static final String SEND = "hasAuthority('INVOICE_SEND')";
    private static final String STATUS = "hasAuthority('INVOICE_STATUS')";
    private static final String EXPORT = "hasAuthority('INVOICE_EXPORT')";
    private static final String PARTNER_MANAGE = "hasAuthority('PARTNER_MANAGE')";
    private static final String PARTNER_READ = "hasAnyAuthority('INVOICE_READ', 'PARTNER_MANAGE')";
    private static final String PRODUCT_MANAGE = "hasAuthority('PRODUCT_MANAGE')";
    private static final String PRODUCT_READ = "hasAnyAuthority('INVOICE_READ', 'PRODUCT_MANAGE')";
    private static final String SETTINGS = "hasAuthority('COMPANY_SETTINGS')";

    private static final Map<String, String> EXPECTED = Map.ofEntries(
            Map.entry("CompanyController.getCompany", MEMBER),
            Map.entry("CompanyController.updateCompany", SETTINGS),
            Map.entry("DocumentController.validate", DRAFT),
            Map.entry("DocumentController.getAll", READ),
            Map.entry("DocumentController.getById", READ),
            Map.entry("DocumentController.getDetails", READ),
            Map.entry("DocumentController.getPdf", READ),
            Map.entry("DocumentController.create", DRAFT),
            Map.entry("DocumentController.update", DRAFT),
            Map.entry("DocumentController.send", SEND),
            Map.entry("DocumentController.reschedule", SEND),
            Map.entry("DocumentController.read", STATUS),
            Map.entry("DocumentController.paid", STATUS),
            Map.entry("DocumentController.markErrorSeen", STATUS),
            Map.entry("DocumentController.delete", DRAFT),
            Map.entry("DownloadJobController.create", EXPORT),
            Map.entry("DownloadJobController.findAll", EXPORT),
            Map.entry("DownloadJobController.retry", EXPORT),
            Map.entry("DownloadJobController.delete", EXPORT),
            Map.entry("DownloadJobController.download", EXPORT),
            Map.entry("PartnerController.search", PARTNER_READ),
            Map.entry("PartnerController.getParties", PARTNER_READ),
            Map.entry("PartnerController.updatePartner", PARTNER_MANAGE),
            Map.entry("PartnerController.createPartner", PARTNER_MANAGE),
            Map.entry("PartnerController.deletePartner", PARTNER_MANAGE),
            Map.entry("ProductController.getParties", PRODUCT_READ),
            Map.entry("ProductController.updateProduct", PRODUCT_MANAGE),
            Map.entry("ProductController.createProduct", PRODUCT_MANAGE),
            Map.entry("ProductController.deleteProduct", PRODUCT_MANAGE),
            Map.entry("ProductCategoryController.listRoot", PRODUCT_READ),
            Map.entry("ProductCategoryController.listAllFlat", PRODUCT_READ),
            Map.entry("ProductCategoryController.getCategory", PRODUCT_READ),
            Map.entry("ProductCategoryController.create", PRODUCT_MANAGE),
            Map.entry("ProductCategoryController.update", PRODUCT_MANAGE),
            Map.entry("ProductCategoryController.delete", PRODUCT_MANAGE),
            Map.entry("AccountantController.linkCustomer", ADMIN),
            Map.entry("AccountantController.confirmLink", ADMIN),
            Map.entry("AccountantController.getCustomersForAccountant", MEMBER),
            Map.entry("AccountantController.getCustomerDocuments", MEMBER),
            Map.entry("InvoiceVatReasonSelectionController.create", DRAFT),
            Map.entry("SponsorController.createSponsorInvoice", ADMIN),
            Map.entry("StatisticsController.getAccountTotals", READ),
            Map.entry("WelcomeNotificationController.getWelcomeNotifications", MEMBER)
    );

    @Test
    void everySapiRouteDeclaresItsPermission() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Map<String, String> actual = new TreeMap<>();

        for (var candidate : scanner.findCandidateComponents("org.letspeppol.app.controller")) {
            Class<?> controller = ClassUtils.resolveClassName(candidate.getBeanClassName(), getClass().getClassLoader());
            RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null || !isSapi(classMapping, mapping)) continue;
                PreAuthorize rule = method.getAnnotation(PreAuthorize.class);
                actual.put(controller.getSimpleName() + "." + method.getName(), rule == null ? "" : rule.value());
            }
        }

        assertThat(actual).containsExactlyInAnyOrderEntriesOf(EXPECTED);
    }

    private static boolean isSapi(RequestMapping classMapping, RequestMapping methodMapping) {
        return startsWithSapi(classMapping) || startsWithSapi(methodMapping);
    }

    private static boolean startsWithSapi(RequestMapping mapping) {
        if (mapping == null) return false;
        String[] paths = mapping.path().length == 0 ? mapping.value() : mapping.path();
        return Arrays.stream(paths).anyMatch(path -> path.startsWith("/sapi"));
    }
}
