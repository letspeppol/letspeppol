package io.tubs.app.controller;

import io.tubs.app.dto.ProductCategoryDto;
import io.tubs.app.service.ProductCategoryService;
import io.tubs.app.util.JwtUtil;
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
        String companyNumber = JwtUtil.getCompanyNumber(jwt);
        return categoryService.listRootCategories(companyNumber, deep);
    }

    @GetMapping("/all")
    public List<ProductCategoryDto> listAllFlat(@AuthenticationPrincipal Jwt jwt) {
        String companyNumber = JwtUtil.getCompanyNumber(jwt);
        return categoryService.listAllFlat(companyNumber);
    }

    @GetMapping("/{id}")
    public ProductCategoryDto getCategory(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @RequestParam(name = "deep", defaultValue = "false") boolean deep) {
        String companyNumber = JwtUtil.getCompanyNumber(jwt);
        return categoryService.getCategory(companyNumber, id, deep);
    }

    @PostMapping
    public ProductCategoryDto create(@AuthenticationPrincipal Jwt jwt, @RequestBody ProductCategoryDto dto) {
        String companyNumber = JwtUtil.getCompanyNumber(jwt);
        return categoryService.createCategory(companyNumber, dto);
    }

    @PutMapping("/{id}")
    public ProductCategoryDto update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @RequestBody ProductCategoryDto dto) {
        String companyNumber = JwtUtil.getCompanyNumber(jwt);
        return categoryService.updateCategory(companyNumber, id, dto);
    }

    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        String companyNumber = JwtUtil.getCompanyNumber(jwt);
        categoryService.deleteCategory(companyNumber, id);
    }
}

