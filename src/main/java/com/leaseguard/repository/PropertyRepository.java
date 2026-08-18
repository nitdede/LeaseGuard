package com.leaseguard.repository;

import com.leaseguard.model.Property;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PropertyRepository extends JpaRepository<Property, Long> {

    Optional<Property> findByExternalId(String externalId);

    List<Property> findAllByOrderByNameAsc();

    @Query("SELECT DISTINCT p.city FROM Property p ORDER BY p.city")
    List<String> findDistinctCities();
}
