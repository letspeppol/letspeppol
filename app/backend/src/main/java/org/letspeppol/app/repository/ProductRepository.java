package org.letspeppol.app.repository;

import org.letspeppol.app.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @Query("SELECT product FROM Product product WHERE product.company.peppolId = :peppolId ORDER BY product.name DESC")
    List<Product> findByOwningCompany(String peppolId);

    @Query("SELECT product FROM Product product WHERE product.id = :id AND product.company.peppolId = :peppolId")
    Optional<Product> findByIdAndCompanyPeppolId(Long id, String peppolId);

    @Modifying
    @Query("DELETE FROM Product product WHERE product.id = :id AND product.company.peppolId = :peppolId")
    void deleteForOwningCompany(String peppolId, Long id);
}