package org.letspeppol.kyc;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.config.OpenApiConfig;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards both generated OpenAPI quality and Mermaid endpoint coverage. */
class OpenApiDocumentationTest {

    @Test
    void everyKycApiHasExplanationAndAppearsInNetworkFlows() throws Exception {
        assertDocumentedControllers("org.letspeppol.kyc.controller", "/kyc");
    }

    @Test
    void openApiExplainsPkceAndServiceCredentials() {
        OpenAPI api = new OpenApiConfig().kycOpenApi();

        assertThat(api.getInfo().getDescription())
                .contains("Authorization Code with PKCE", "client-credentials", "/sapi/**", "errorCode");
        assertThat(api.getComponents().getSecuritySchemes())
                .containsKeys("oauth2", "serviceAuth", "bearerAuth");
        assertThat(api.getServers()).extracting(server -> server.getUrl()).containsExactly("/kyc");
        assertThat(api.getComponents().getSecuritySchemes().get("oauth2")
                .getFlows().getAuthorizationCode().getAuthorizationUrl()).isEqualTo("/kyc/oauth2/authorize");
    }

    private static void assertDocumentedControllers(String packageName, String externalPrefix) throws Exception {
        String flows = readLinkedFlowDocumentation();
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<String> missing = new ArrayList<>();
        for (var candidate : scanner.findCandidateComponents(packageName)) {
            Class<?> controller = ClassUtils.resolveClassName(
                    candidate.getBeanClassName(), OpenApiDocumentationTest.class.getClassLoader());
            assertLocalControlApisHidden(controller);
            if (AnnotatedElementUtils.hasAnnotation(controller, Hidden.class)) continue;

            Tag tag = AnnotatedElementUtils.findMergedAnnotation(controller, Tag.class);
            assertThat(tag).as("@Tag on %s", controller.getSimpleName()).isNotNull();
            assertThat(tag.description()).as("tag description on %s", controller.getSimpleName()).isNotBlank();

            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null || AnnotatedElementUtils.hasAnnotation(method, Hidden.class)) continue;
                Operation operation = AnnotatedElementUtils.findMergedAnnotation(method, Operation.class);
                assertThat(operation).as("@Operation on %s.%s", controller.getSimpleName(), method.getName()).isNotNull();
                assertThat(operation.summary()).as("summary on %s.%s", controller.getSimpleName(), method.getName()).isNotBlank();
                assertThat(operation.description()).as("description on %s.%s", controller.getSimpleName(), method.getName()).isNotBlank();

                for (String path : paths(controller, mapping)) {
                    String externalPath = externalPrefix + path;
                    if (!flows.contains(externalPath)) missing.add(externalPath);
                }
            }
        }
        assertThat(missing).as("controller paths missing from API network flow pages").isEmpty();
    }

    private static List<String> paths(Class<?> controller, RequestMapping methodMapping) {
        RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
        String[] roots = classMapping == null || values(classMapping).length == 0
                ? new String[]{""} : values(classMapping);
        String[] leaves = values(methodMapping).length == 0 ? new String[]{""} : values(methodMapping);
        List<String> result = new ArrayList<>();
        for (String root : roots) for (String leaf : leaves) result.add(join(root, leaf));
        return result;
    }

    private static void assertLocalControlApisHidden(Class<?> controller) {
        boolean controllerHidden = AnnotatedElementUtils.hasAnnotation(controller, Hidden.class);
        for (Method method : controller.getDeclaredMethods()) {
            RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
            if (mapping == null) continue;
            boolean operationHidden = controllerHidden
                    || AnnotatedElementUtils.hasAnnotation(method, Hidden.class);
            for (String path : paths(controller, mapping)) {
                if (path.equals("/lapi") || path.startsWith("/lapi/")) {
                    assertThat(operationHidden)
                            .as("local-control operation %s.%s (%s) must be @Hidden",
                                    controller.getSimpleName(), method.getName(), path)
                            .isTrue();
                }
            }
        }
    }

    private static String[] values(RequestMapping mapping) {
        return Arrays.stream(mapping.path().length == 0 ? mapping.value() : mapping.path())
                .toArray(String[]::new);
    }

    private static String join(String root, String leaf) {
        String joined = ("/" + root + "/" + leaf).replaceAll("/++", "/");
        return joined.length() > 1 && joined.endsWith("/") ? joined.substring(0, joined.length() - 1) : joined;
    }

    private static Path findFlowsDocument() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("readme/api-network-flows.md");
            if (Files.isRegularFile(candidate)) return candidate;
            current = current.getParent();
        }
        throw new AssertionError("Cannot locate readme/api-network-flows.md");
    }

    private static String readLinkedFlowDocumentation() throws Exception {
        Path guide = findFlowsDocument();
        String guideContent = Files.readString(guide);
        Matcher links = Pattern.compile("\\[[^]]+]\\(\\./(api-[^)]+\\.md)\\)").matcher(guideContent);
        StringBuilder documentation = new StringBuilder(guideContent);
        int pageCount = 0;
        while (links.find()) {
            Path page = guide.getParent().resolve(links.group(1)).normalize();
            assertThat(page).as("API flow page linked from %s", guide).isRegularFile();
            String content = Files.readString(page);
            assertThat(content).as("content of %s", page).contains("```mermaid", "Executable proof:");
            documentation.append('\n').append(content);
            pageCount++;
        }
        assertThat(pageCount).as("API flow pages linked from %s", guide).isPositive();
        return documentation.toString();
    }
}
