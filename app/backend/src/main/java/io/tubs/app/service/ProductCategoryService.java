package io.tubs.app.service;

import io.tubs.app.dto.ProductCategoryDto;
import io.tubs.app.exception.NotFoundException;
import io.tubs.app.mapper.ProductCategoryMapper;
import io.tubs.app.model.Company;
import io.tubs.app.model.ProductCategory;
import io.tubs.app.repository.CompanyRepository;
import io.tubs.app.repository.ProductCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ProductCategoryService {

    private final ProductCategoryRepository categoryRepository;
    private final CompanyRepository companyRepository;

    public List<ProductCategoryDto> listRootCategories(String companyNumber, boolean deep) {
        return categoryRepository.findRootByCompany(companyNumber).stream()
                .map(c -> ProductCategoryMapper.toDto(c, deep))
                .toList();
    }

    public List<ProductCategoryDto> listAllFlat(String companyNumber) {
        return categoryRepository.findAllByCompany(companyNumber).stream()
                .map(ProductCategoryMapper::toDto)
                .toList();
    }

    public ProductCategoryDto getCategory(String companyNumber, Long id, boolean deep) {
        ProductCategory category;
        if (deep) {
            category = categoryRepository.fetchWithChildren(id, companyNumber)
                    .orElseThrow(() -> new NotFoundException("Category does not exist"));
        } else {
            category = categoryRepository.findByIdAndCompany(id, companyNumber)
                    .orElseThrow(() -> new NotFoundException("Category does not exist"));
        }
        return ProductCategoryMapper.toDto(category, deep);
    }

    public ProductCategoryDto createCategory(String companyNumber, ProductCategoryDto dto) {
        Company company = companyRepository.findByCompanyNumber(companyNumber)
                .orElseThrow(() -> new NotFoundException("Company does not exist"));

        ProductCategory category = new ProductCategory();
        category.setName(dto.name());
        category.setColor(dto.color());
        category.setCompany(company);

        if (dto.parentId() != null) {
            ProductCategory parent = categoryRepository.findByIdAndCompany(dto.parentId(), companyNumber)
                    .orElseThrow(() -> new NotFoundException("Parent category does not exist"));
            category.setParent(parent);
            parent.getSubcategories().add(category);
        }

        categoryRepository.save(category);
        return ProductCategoryMapper.toDto(category, false);
    }

    public ProductCategoryDto updateCategory(String companyNumber, Long id, ProductCategoryDto dto) {
        ProductCategory category = categoryRepository.findByIdAndCompany(id, companyNumber)
                .orElseThrow(() -> new NotFoundException("Category does not exist"));

        category.setName(dto.name());
        category.setColor(dto.color());

        if (!Objects.equals(dto.parentId(), category.getParent() != null ? category.getParent().getId() : null)) {
            // detach from old parent
            if (category.getParent() != null) {
                category.getParent().getSubcategories().remove(category);
            }
            if (dto.parentId() != null) {
                ProductCategory newParent = categoryRepository.findByIdAndCompany(dto.parentId(), companyNumber)
                        .orElseThrow(() -> new NotFoundException("Parent category does not exist"));
                // prevent cycles
                if (createsCycle(category, newParent)) {
                    throw new IllegalArgumentException("Cannot assign a descendant as parent");
                }
                category.setParent(newParent);
                newParent.getSubcategories().add(category);
            } else {
                category.setParent(null);
            }
        }

        categoryRepository.save(category);
        return ProductCategoryMapper.toDto(category, false);
    }

    public void deleteCategory(String companyNumber, Long id) {
        ProductCategory category = categoryRepository.findByIdAndCompany(id, companyNumber)
                .orElseThrow(() -> new NotFoundException("Category does not exist"));
        if (category.getParent() != null) {
            category.getParent().getSubcategories().remove(category);
        }
        categoryRepository.delete(category);
    }

    private boolean createsCycle(ProductCategory current, ProductCategory newParent) {
        ProductCategory walker = newParent;
        while (walker != null) {
            if (walker.getId() != null && walker.getId().equals(current.getId())) {
                return true;
            }
            walker = walker.getParent();
        }
        return false;
    }
}
