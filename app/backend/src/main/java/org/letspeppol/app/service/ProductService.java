package org.letspeppol.app.service;

import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.app.dto.ProductDto;
import org.letspeppol.app.exception.NotFoundException;
import org.letspeppol.app.mapper.ProductMapper;
import org.letspeppol.app.model.Company;
import org.letspeppol.app.model.Product;
import org.letspeppol.app.model.ProductCategory;
import org.letspeppol.app.repository.CompanyRepository;
import org.letspeppol.app.repository.ProductCategoryRepository;
import org.letspeppol.app.repository.ProductRepository;
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
    private final Counter productCreateCounter;

    public List<ProductDto> findByPeppolId(String peppolId) {
        return productRepository.findByOwningCompany(peppolId).stream()
                .map(ProductMapper::toDto)
                .toList();
    }

    public ProductDto createProduct(String peppolId, ProductDto productDto) {
        Company company = companyRepository.findByPeppolId(peppolId).orElseThrow(() -> new NotFoundException("Company does not exist"));

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
        productCreateCounter.increment();
        return ProductMapper.toDto(product);
    }

    public ProductDto updateProduct(String peppolId, Long id, ProductDto productDto) {
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
            if (!category.getCompany().getPeppolId().equals(peppolId)) {
                throw new NotFoundException("Category does not exist");
            }
            product.setCategory(category);
        } else {
            product.setCategory(null);
        }

        productRepository.save(product);
        return ProductMapper.toDto(product);
    }

    public void deleteProduct(String peppolId, Long id) {
        productRepository.deleteForOwningCompany(peppolId, id);
    }
}
