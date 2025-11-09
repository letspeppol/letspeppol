package io.tubs.app.service;

import io.tubs.app.dto.ProductDto;
import io.tubs.app.exception.NotFoundException;
import io.tubs.app.mapper.ProductMapper;
import io.tubs.app.model.Company;
import io.tubs.app.model.Product;
import io.tubs.app.model.ProductCategory;
import io.tubs.app.repository.CompanyRepository;
import io.tubs.app.repository.ProductCategoryRepository;
import io.tubs.app.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Transactional
@Service
public class ProductService {

    private final CompanyRepository companyRepository;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository productCategoryRepository;

    public List<ProductDto> findByCompanyNumber(String companyNumber) {
        return productRepository.findByOwningCompany(companyNumber).stream()
                .map(ProductMapper::toDto)
                .toList();
    }

    public ProductDto createProduct(String companyNumber, ProductDto productDto) {
        Company company = companyRepository.findByCompanyNumber(companyNumber).orElseThrow(() -> new NotFoundException("Company does not exist"));

        Product product = new Product();
        product.setName(productDto.name());
        product.setDescription(productDto.description());
        product.setReference(productDto.reference());
        product.setBarcode(productDto.barcode());
        product.setCostPrice(productDto.costPrice());
        product.setSalePrice(productDto.salePrice());
        product.setTaxPercentage(productDto.taxPercentage());
        product.setCompany(company);

        if (productDto.categoryId() != null) {
            ProductCategory category = productCategoryRepository.findById(productDto.categoryId())
                    .orElseThrow(() -> new NotFoundException("Category does not exist"));
            if (!category.getCompany().getId().equals(company.getId())) {
                throw new NotFoundException("Category does not exist"); // hide cross-company category
            }
            product.setCategory(category);
        }

        productRepository.save(product);
        return ProductMapper.toDto(product);
    }

    public ProductDto updateProduct(String companyNumber, Long id, ProductDto productDto) {
        Product product = productRepository.findById(id).orElseThrow(() -> new NotFoundException("Product does not exist"));

        product.setName(productDto.name());
        product.setDescription(productDto.description());
        product.setReference(productDto.reference());
        product.setBarcode(productDto.barcode());
        product.setCostPrice(productDto.costPrice());
        product.setSalePrice(productDto.salePrice());
        product.setTaxPercentage(productDto.taxPercentage());

        if (productDto.categoryId() != null) {
            ProductCategory category = productCategoryRepository.findById(productDto.categoryId())
                    .orElseThrow(() -> new NotFoundException("Category does not exist"));
            if (!category.getCompany().getCompanyNumber().equals(companyNumber)) {
                throw new NotFoundException("Category does not exist");
            }
            product.setCategory(category);
        } else {
            product.setCategory(null);
        }

        productRepository.save(product);
        return ProductMapper.toDto(product);
    }

    public void deleteProduct(String companyNumber, Long id) {
        productRepository.deleteForOwningCompany(companyNumber, id);
    }
}
