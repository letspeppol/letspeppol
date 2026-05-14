package org.letspeppol.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.letspeppol.app.dto.ProductCategoryDto;
import org.letspeppol.app.service.ProductCategoryService;
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
@RequestMapping("/sapi/product-category")
@Tag(name = "App Product Categories", description = "Endpoints for maintaining the product category tree used to organize products in the application.")
@SecurityRequirement(name = "bearerAuth")
public class ProductCategoryController {

    private final ProductCategoryService categoryService;

    @GetMapping
    @Operation(summary = "List root categories", description = "Returns the top-level product categories for the authenticated company, optionally expanded recursively.")
    public List<ProductCategoryDto> listRoot(@AuthenticationPrincipal Jwt jwt, @RequestParam(name = "deep", defaultValue = "false") boolean deep) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return categoryService.listRootCategories(peppolId, deep);
    }

    @GetMapping("/all")
    @Operation(summary = "List all categories flat", description = "Returns all product categories for the authenticated company as a flat list.")
    public List<ProductCategoryDto> listAllFlat(@AuthenticationPrincipal Jwt jwt) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return categoryService.listAllFlat(peppolId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get category by id", description = "Returns one product category for the authenticated company, optionally with its nested children.")
    public ProductCategoryDto getCategory(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @RequestParam(name = "deep", defaultValue = "false") boolean deep) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return categoryService.getCategory(peppolId, id, deep);
    }

    @PostMapping
    @Operation(summary = "Create category", description = "Creates a new product category for the authenticated company.")
    public ProductCategoryDto create(@AuthenticationPrincipal Jwt jwt, @RequestBody ProductCategoryDto dto) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return categoryService.createCategory(peppolId, dto);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update category", description = "Updates an existing product category owned by the authenticated company.")
    public ProductCategoryDto update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @RequestBody ProductCategoryDto dto) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return categoryService.updateCategory(peppolId, id, dto);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete category", description = "Deletes a product category owned by the authenticated company.")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        categoryService.deleteCategory(peppolId, id);
    }
}

