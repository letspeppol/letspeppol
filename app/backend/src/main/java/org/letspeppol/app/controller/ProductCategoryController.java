package org.letspeppol.app.controller;

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
@RequestMapping("/api/product-category")
public class ProductCategoryController {

    private final ProductCategoryService categoryService;

    @GetMapping
    public List<ProductCategoryDto> listRoot(@AuthenticationPrincipal Jwt jwt, @RequestParam(name = "deep", defaultValue = "false") boolean deep) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return categoryService.listRootCategories(peppolId, deep);
    }

    @GetMapping("/all")
    public List<ProductCategoryDto> listAllFlat(@AuthenticationPrincipal Jwt jwt) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return categoryService.listAllFlat(peppolId);
    }

    @GetMapping("/{id}")
    public ProductCategoryDto getCategory(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @RequestParam(name = "deep", defaultValue = "false") boolean deep) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return categoryService.getCategory(peppolId, id, deep);
    }

    @PostMapping
    public ProductCategoryDto create(@AuthenticationPrincipal Jwt jwt, @RequestBody ProductCategoryDto dto) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return categoryService.createCategory(peppolId, dto);
    }

    @PutMapping("/{id}")
    public ProductCategoryDto update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @RequestBody ProductCategoryDto dto) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return categoryService.updateCategory(peppolId, id, dto);
    }

    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        categoryService.deleteCategory(peppolId, id);
    }
}

