package com.leaseguard.repository;

import com.leaseguard.dto.LeaseFilters;
import com.leaseguard.model.Lease;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Builds the database-level portion of {@link LeaseFilters} as a JPA {@link Specification}. */
public final class LeaseSpecifications {

    private LeaseSpecifications() {
    }

    public static Specification<Lease> from(LeaseFilters filters, LocalDate asOf) {
        List<Specification<Lease>> conditions = new ArrayList<>();
        if (filters.propertyId() != null) {
            conditions.add((root, query, cb) -> cb.equal(root.get("property").get("id"), filters.propertyId()));
        }
        if (filters.city() != null && !filters.city().isBlank()) {
            conditions.add((root, query, cb) -> cb.equal(root.get("property").get("city"), filters.city()));
        }
        if (filters.status() != null) {
            conditions.add((root, query, cb) -> cb.equal(root.get("status"), filters.status()));
        }
        if (filters.assignedManager() != null && !filters.assignedManager().isBlank()) {
            conditions.add((root, query, cb) -> cb.equal(root.get("assignedManager"), filters.assignedManager()));
        }
        if (filters.expiringWithinDays() != null) {
            LocalDate cutoff = asOf.plusDays(filters.expiringWithinDays());
            conditions.add((root, query, cb) -> cb.lessThanOrEqualTo(root.get("endDate"), cutoff));
        }
        return Specification.allOf(conditions);
    }
}
