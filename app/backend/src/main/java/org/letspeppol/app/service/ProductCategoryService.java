package org.letspeppol.app.service;

import org.letspeppol.app.dto.ProductCategoryDto;
import org.letspeppol.app.exception.NotFoundException;
import org.letspeppol.app.mapper.ProductCategoryMapper;
import org.letspeppol.app.model.Company;
import org.letspeppol.app.model.ProductCategory;
import org.letspeppol.app.repository.CompanyRepository;
import org.letspeppol.app.repository.ProductCategoryRepository;
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

    public List<ProductCategoryDto> listRootCategories(String peppolId, boolean deep) {
        return categoryRepository.findRootByCompany(peppolId).stream()
                .map(c -> ProductCategoryMapper.toDto(c, deep))
                .toList();
    }

    public List<ProductCategoryDto> listAllFlat(String peppolId) {
        return categoryRepository.findAllByCompany(peppolId).stream()
                .map(ProductCategoryMapper::toDto)
                .toList();
    }

    public ProductCategoryDto getCategory(String peppolId, Long id, boolean deep) {
        ProductCategory category;
        if (deep) {
            category = categoryRepository.fetchWithChildren(id, peppolId)
                    .orElseThrow(() -> new NotFoundException("Category does not exist"));
        } else {
            category = categoryRepository.findByIdAndCompany(id, peppolId)
                    .orElseThrow(() -> new NotFoundException("Category does not exist"));
        }
        return ProductCategoryMapper.toDto(category, deep);
    }

    public ProductCategoryDto createCategory(String peppolId, ProductCategoryDto dto) {
        Company company = companyRepository.findByPeppolId(peppolId)
                .orElseThrow(() -> new NotFoundException("Company does not exist"));

        ProductCategory category = new ProductCategory();
        category.setName(dto.name());
        category.setColor(dto.color());
        category.setCompany(company);

        if (dto.parentId() != null) {
            ProductCategory parent = categoryRepository.findByIdAndCompany(dto.parentId(), peppolId)
                    .orElseThrow(() -> new NotFoundException("Parent category does not exist"));
            category.setParent(parent);
            parent.getSubcategories().add(category);
        }

        category = categoryRepository.save(category);
        return ProductCategoryMapper.toDto(category, false);
    }

    public ProductCategoryDto updateCategory(String peppolId, Long id, ProductCategoryDto dto) {
        ProductCategory category = categoryRepository.findByIdAndCompany(id, peppolId)
                .orElseThrow(() -> new NotFoundException("Category does not exist"));

        category.setName(dto.name());
        category.setColor(dto.color());

        if (!Objects.equals(dto.parentId(), category.getParent() != null ? category.getParent().getId() : null)) {
            // detach from old parent
            if (category.getParent() != null) {
                category.getParent().getSubcategories().remove(category);
            }
            if (dto.parentId() != null) {
                ProductCategory newParent = categoryRepository.findByIdAndCompany(dto.parentId(), peppolId)
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

        category = categoryRepository.save(category);
        return ProductCategoryMapper.toDto(category, false);
    }

    public void deleteCategory(String peppolId, Long id) {
        ProductCategory category = categoryRepository.findByIdAndCompany(id, peppolId)
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
