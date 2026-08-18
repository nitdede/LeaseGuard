package com.leaseguard.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Covers the idempotency comparison logic used by CSV reimport to classify NEW/CHANGED/UNCHANGED. */
class LeaseTest {

    private final Property property = new Property("PROP-1", "Tower", PropertyType.OFFICE, "1 Main St", "Dallas",
            "TX", "75001", 100_000);
    private final Tenant tenant = new Tenant("TEN-1", "Acme Inc", "Technology");

    @Test
    void differsFromReturnsFalseWhenAllComparedFieldsMatch() {
        Lease lease = lease();

        boolean differs = lease.differsFrom("PROP-1", "TEN-1", 5000, LocalDate.of(2023, 1, 1),
                LocalDate.of(2028, 1, 1), LocalDate.of(2027, 7, 1), BigDecimal.valueOf(500000), LeaseStatus.MONITOR,
                "Priya Shah", LocalDate.of(2026, 6, 1));

        assertThat(differs).isFalse();
    }

    @Test
    void differsFromReturnsTrueWhenAnnualRentChanges() {
        Lease lease = lease();

        boolean differs = lease.differsFrom("PROP-1", "TEN-1", 5000, LocalDate.of(2023, 1, 1),
                LocalDate.of(2028, 1, 1), LocalDate.of(2027, 7, 1), BigDecimal.valueOf(550000), LeaseStatus.MONITOR,
                "Priya Shah", LocalDate.of(2026, 6, 1));

        assertThat(differs).isTrue();
    }

    @Test
    void differsFromIgnoresScaleDifferencesInMoneyComparison() {
        Lease lease = lease(); // annualBaseRent = 500000

        boolean differs = lease.differsFrom("PROP-1", "TEN-1", 5000, LocalDate.of(2023, 1, 1),
                LocalDate.of(2028, 1, 1), LocalDate.of(2027, 7, 1), new BigDecimal("500000.00"), LeaseStatus.MONITOR,
                "Priya Shah", LocalDate.of(2026, 6, 1));

        assertThat(differs).isFalse();
    }

    @Test
    void differsFromDetectsManagerUnassignment() {
        Lease lease = lease(); // assignedManager = "Priya Shah"

        boolean differs = lease.differsFrom("PROP-1", "TEN-1", 5000, LocalDate.of(2023, 1, 1),
                LocalDate.of(2028, 1, 1), LocalDate.of(2027, 7, 1), BigDecimal.valueOf(500000), LeaseStatus.MONITOR,
                null, LocalDate.of(2026, 6, 1));

        assertThat(differs).isTrue();
    }

    @Test
    void differsFromDetectsPropertyReassignment() {
        Lease lease = lease(); // linked to PROP-1

        boolean differs = lease.differsFrom("PROP-2", "TEN-1", 5000, LocalDate.of(2023, 1, 1),
                LocalDate.of(2028, 1, 1), LocalDate.of(2027, 7, 1), BigDecimal.valueOf(500000), LeaseStatus.MONITOR,
                "Priya Shah", LocalDate.of(2026, 6, 1));

        assertThat(differs).isTrue();
    }

    @Test
    void differsFromDetectsTenantReassignment() {
        Lease lease = lease(); // linked to TEN-1

        boolean differs = lease.differsFrom("PROP-1", "TEN-2", 5000, LocalDate.of(2023, 1, 1),
                LocalDate.of(2028, 1, 1), LocalDate.of(2027, 7, 1), BigDecimal.valueOf(500000), LeaseStatus.MONITOR,
                "Priya Shah", LocalDate.of(2026, 6, 1));

        assertThat(differs).isTrue();
    }

    @Test
    void applyImportedValuesReassignsPropertyAndTenant() {
        Lease lease = lease(); // linked to PROP-1 / TEN-1
        Property newProperty = new Property("PROP-2", "Riverside", PropertyType.RETAIL, "9 River Rd", "Austin",
                "TX", "78701", 50_000);
        Tenant newTenant = new Tenant("TEN-2", "Globex Corp", "Retail");

        lease.applyImportedValues(newProperty, newTenant, 5000, LocalDate.of(2023, 1, 1), LocalDate.of(2028, 1, 1),
                LocalDate.of(2027, 7, 1), BigDecimal.valueOf(500000), LeaseStatus.MONITOR, "Priya Shah",
                LocalDate.of(2026, 6, 1));

        assertThat(lease.getProperty()).isEqualTo(newProperty);
        assertThat(lease.getTenant()).isEqualTo(newTenant);
    }

    @Test
    void propertyConflictsWithReturnsFalseForIdenticalAttributes() {
        boolean conflict = property.conflictsWith("Tower", PropertyType.OFFICE, "1 Main St", "Dallas", "TX",
                "75001", 100_000);

        assertThat(conflict).isFalse();
    }

    @Test
    void propertyConflictsWithReturnsTrueWhenSquareFootageDiffers() {
        boolean conflict = property.conflictsWith("Tower", PropertyType.OFFICE, "1 Main St", "Dallas", "TX",
                "75001", 120_000);

        assertThat(conflict).isTrue();
    }

    private Lease lease() {
        return new Lease("LSE-1", property, tenant, 5000, LocalDate.of(2023, 1, 1), LocalDate.of(2028, 1, 1),
                LocalDate.of(2027, 7, 1), BigDecimal.valueOf(500000), LeaseStatus.MONITOR, "Priya Shah",
                LocalDate.of(2026, 6, 1));
    }
}
