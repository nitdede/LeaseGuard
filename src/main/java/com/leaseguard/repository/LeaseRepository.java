package com.leaseguard.repository;

import com.leaseguard.model.Lease;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface LeaseRepository extends JpaRepository<Lease, Long>, JpaSpecificationExecutor<Lease> {

    Optional<Lease> findByExternalId(String externalId);

    @Override
    @EntityGraph(attributePaths = {"property", "tenant"})
    List<Lease> findAll(org.springframework.data.jpa.domain.Specification<Lease> spec);

    @Override
    @EntityGraph(attributePaths = {"property", "tenant"})
    Optional<Lease> findById(Long id);

    /**
     * Per-property sum of annual base rent across all leases, used to compute each tenant's
     * rent-concentration share for the risk score. Computed in one query rather than per-lease
     * to avoid N+1 lookups.
     */
    @Query("SELECT l.property.id AS propertyId, SUM(l.annualBaseRent) AS totalAnnualBaseRent "
            + "FROM Lease l GROUP BY l.property.id")
    List<PropertyRentTotal> sumAnnualBaseRentByProperty();

    @Query("SELECT DISTINCT l.assignedManager FROM Lease l WHERE l.assignedManager IS NOT NULL ORDER BY l.assignedManager")
    List<String> findDistinctAssignedManagers();

    interface PropertyRentTotal {
        Long getPropertyId();

        BigDecimal getTotalAnnualBaseRent();
    }
}
