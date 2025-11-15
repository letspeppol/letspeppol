package org.letspeppol.app.repository;

import org.letspeppol.app.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @Query("SELECT product FROM Product product WHERE product.company.companyNumber = :companyNumber ORDER BY product.name DESC")
    List<Product> findByOwningCompany(String companyNumber);

    @Modifying
    @Query("DELETE FROM Product product WHERE product.id = :id AND product.company.companyNumber = :companyNumber")
    void deleteForOwningCompany(String companyNumber, Long id);
}