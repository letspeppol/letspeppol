package org.letspeppol.app.controller;

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
@RequestMapping("/api/product")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public List<ProductDto> getParties(@AuthenticationPrincipal Jwt jwt) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return productService.findByPeppolId(peppolId);
    }

    @PutMapping("{id}")
    public ProductDto updateProduct(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @RequestBody ProductDto productDto) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return productService.updateProduct(peppolId, id, productDto);
    }

    @PostMapping
    public ProductDto createProduct(@AuthenticationPrincipal Jwt jwt, @RequestBody ProductDto productDto) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return productService.createProduct(peppolId, productDto);
    }

    @DeleteMapping("{id}")
    public void deleteProduct(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        productService.deleteProduct(peppolId, id);
    }
}