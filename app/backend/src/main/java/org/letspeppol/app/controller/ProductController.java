package org.letspeppol.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.letspeppol.app.dto.ProductDto;
import org.letspeppol.app.service.ProductService;
import org.letspeppol.app.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/sapi/product")
@Tag(name = "App Products", description = "Product catalog endpoints used to manage reusable invoice and catalog items in the application.")
@SecurityRequirement(name = "bearerAuth")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "List products", description = "Returns the product catalog owned by the authenticated company.")
    public List<ProductDto> getParties(@AuthenticationPrincipal Jwt jwt) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return productService.findByPeppolId(peppolId);
    }

    @PutMapping("{id}")
    @Operation(summary = "Update product", description = "Updates one product record in the authenticated company's catalog.")
    public ProductDto updateProduct(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @RequestBody ProductDto productDto) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return productService.updateProduct(peppolId, id, productDto);
    }

    @PostMapping
    @Operation(summary = "Create product", description = "Creates a new product in the authenticated company's catalog.")
    public ProductDto createProduct(@AuthenticationPrincipal Jwt jwt, @RequestBody ProductDto productDto) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return productService.createProduct(peppolId, productDto);
    }

    @DeleteMapping("{id}")
    @Operation(summary = "Delete product", description = "Deletes a product from the authenticated company's catalog.")
    public void deleteProduct(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        productService.deleteProduct(peppolId, id);
    }
}
