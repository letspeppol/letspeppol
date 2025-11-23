package org.letspeppol.app.repository;

import org.letspeppol.app.model.ProductCategory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    @Query("select c from ProductCategory c where c.company.peppolId = :peppolId and c.parent is null order by c.name asc")
    List<ProductCategory> findRootByCompany(String peppolId);

    @Query("select c from ProductCategory c where c.company.peppolId = :peppolId order by c.name asc")
    List<ProductCategory> findAllByCompany(String peppolId);

    @Query("select c from ProductCategory c where c.id = :id and c.company.peppolId = :peppolId")
    Optional<ProductCategory> findByIdAndCompany(Long id, String peppolId);

    @EntityGraph(attributePaths = {"subcategories"})
    @Query("select c from ProductCategory c where c.id = :id and c.company.peppolId = :peppolId")
    Optional<ProductCategory> fetchWithChildren(Long id, String peppolId);
}

