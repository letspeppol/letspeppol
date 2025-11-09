package io.tubs.app.controller;

import io.tubs.app.dto.ProductDto;
import io.tubs.app.service.ProductService;
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
@RequestMapping("/api/product")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public List<ProductDto> getParties(@AuthenticationPrincipal Jwt jwt) {
        String companyNumber = JwtUtil.getCompanyNumber(jwt);
        return productService.findByCompanyNumber(companyNumber);
    }

    @PutMapping("{id}")
    public ProductDto updateProduct(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @RequestBody ProductDto productDto) {
        String companyNumber = JwtUtil.getCompanyNumber(jwt);
        return productService.updateProduct(companyNumber, id, productDto);
    }

    @PostMapping
    public ProductDto createProduct(@AuthenticationPrincipal Jwt jwt, @RequestBody ProductDto productDto) {
        String companyNumber = JwtUtil.getCompanyNumber(jwt);
        return productService.createProduct(companyNumber, productDto);
    }

    @DeleteMapping("{id}")
    public void deleteProduct(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        String companyNumber = JwtUtil.getCompanyNumber(jwt);
        productService.deleteProduct(companyNumber, id);
    }
}