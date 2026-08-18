package com.leaseguard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.leaseguard.AbstractIntegrationTest;
import com.leaseguard.dto.LeaseFilters;
import com.leaseguard.dto.LeaseRiskView;
import com.leaseguard.dto.PagedResult;
import com.leaseguard.model.Lease;
import com.leaseguard.model.LeaseStatus;
import com.leaseguard.model.Property;
import com.leaseguard.model.PropertyType;
import com.leaseguard.model.Tenant;
import com.leaseguard.repository.LeaseRepository;
import com.leaseguard.repository.PropertyRepository;
import com.leaseguard.repository.TenantRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Covers the lease-list "expiring within N days" filter, which is applied in SQL by
 * {@link com.leaseguard.repository.LeaseSpecifications}. The demo as-of date is fixed to
 * 2026-08-17 (see application.yml), so a 90-day window's cutoff is 2026-11-15.
 */
class LeaseListServiceIT extends AbstractIntegrationTest {

    @Autowired
    private LeaseListService leaseListService;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private LeaseRepository leaseRepository;

    @BeforeEach
    void seedLeasesAcrossTheExpirationBoundary() {
        Property property = propertyRepository.save(new Property("PROP-EXP", "Tower", PropertyType.OFFICE,
                "1 Main St", "Dallas", "TX", "75001", 100_000));
        Tenant tenant = tenantRepository.save(new Tenant("TEN-EXP", "Acme Inc", "Technology"));

        save("LSE-EXPIRED", property, tenant, LocalDate.of(2020, 1, 1), LocalDate.of(2026, 1, 1)); // already expired
        save("LSE-AT-ASOF", property, tenant, LocalDate.of(2020, 1, 1), LocalDate.of(2026, 8, 17)); // expires today
        save("LSE-WITHIN-WINDOW", property, tenant, LocalDate.of(2020, 1, 1), LocalDate.of(2026, 10, 1)); // ~45 days out
        save("LSE-AT-CUTOFF", property, tenant, LocalDate.of(2020, 1, 1), LocalDate.of(2026, 11, 15)); // exactly 90 days out
        save("LSE-BEYOND-WINDOW", property, tenant, LocalDate.of(2020, 1, 1), LocalDate.of(2026, 12, 1)); // >90 days out
    }

    @Test
    void expiringWithinDaysExcludesAlreadyExpiredLeasesAndIncludesBothBoundaries() {
        PagedResult<LeaseRiskView> result = leaseListService.list(
                new LeaseFilters(null, null, null, null, 90, null), 0, 50);

        List<String> externalIds = result.items().stream().map(LeaseRiskView::externalId).toList();

        assertThat(externalIds)
                .contains("LSE-AT-ASOF", "LSE-WITHIN-WINDOW", "LSE-AT-CUTOFF")
                .doesNotContain("LSE-EXPIRED", "LSE-BEYOND-WINDOW");
    }

    private void save(String externalId, Property property, Tenant tenant, LocalDate startDate, LocalDate endDate) {
        leaseRepository.save(new Lease(externalId, property, tenant, 5000, startDate, endDate, null,
                BigDecimal.valueOf(500_000), LeaseStatus.MONITOR, "Priya Shah", null));
    }
}
