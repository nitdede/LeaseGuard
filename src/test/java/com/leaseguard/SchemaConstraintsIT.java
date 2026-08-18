package com.leaseguard;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.leaseguard.model.Property;
import com.leaseguard.repository.PropertyRepository;
import com.leaseguard.model.PropertyType;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Verifies database-level invariants that must hold even under concurrent/careless application code. */
class SchemaConstraintsIT extends AbstractIntegrationTest {

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void duplicatePropertyExternalIdViolatesUniqueConstraint() {
        propertyRepository.save(new Property("PROP-DUP", "Tower A", PropertyType.OFFICE, "1 Main St", "Dallas",
                "TX", "75001", 100_000));

        assertThatThrownBy(() -> propertyRepository.saveAndFlush(new Property("PROP-DUP", "Tower B",
                PropertyType.RETAIL, "2 Main St", "Austin", "TX", "78701", 50_000)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void leaseReferencingNonExistentPropertyViolatesForeignKey() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO leases (external_id, property_id, tenant_id, leased_sqft, start_date, end_date, "
                        + "annual_base_rent, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "LSE-ORPHAN", 999_999L, 999_999L, 1000, LocalDate.of(2024, 1, 1), LocalDate.of(2028, 1, 1),
                500_000, "MONITOR"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void endDateNotAfterStartDateViolatesCheckConstraint() {
        Property property = propertyRepository.save(new Property("PROP-CHK", "Tower", PropertyType.OFFICE,
                "1 Main St", "Dallas", "TX", "75001", 100_000));
        jdbcTemplate.update("INSERT INTO tenants (external_id, name) VALUES (?, ?)", "TEN-CHK", "Acme");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO leases (external_id, property_id, tenant_id, leased_sqft, start_date, end_date, "
                        + "annual_base_rent, status) VALUES (?, (SELECT id FROM properties WHERE external_id = ?), "
                        + "(SELECT id FROM tenants WHERE external_id = ?), ?, ?, ?, ?, ?)",
                "LSE-BADDATES", property.getExternalId(), "TEN-CHK", 1000, LocalDate.of(2028, 1, 1),
                LocalDate.of(2024, 1, 1), 500_000, "MONITOR"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
