package com.leaseguard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.leaseguard.AbstractIntegrationTest;
import com.leaseguard.dto.DashboardView;
import com.leaseguard.dto.LeaseFilters;
import com.leaseguard.dto.PagedResult;
import com.leaseguard.dto.RiskLevel;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Loads the bundled demo portfolio into a real database and checks that dashboard aggregates and
 * lease-list filters/pagination agree with independently computed expectations (see
 * {@code docs/data-dictionary.md} and the README for the same numbers, verified against the CSV
 * with a standalone script during development).
 */
class DashboardServiceIT extends AbstractIntegrationTest {

    @Autowired
    private ImportService importService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private LeaseListService leaseListService;

    @BeforeEach
    void importDemoPortfolio() throws Exception {
        String content = Files.readString(Path.of("data/demo/lease_portfolio.csv"));
        var preview = importService.previewUpload("f.csv", content);
        importService.commit(preview.filename(), preview.content());
    }

    @Test
    void dashboardAggregatesMatchIndependentlyComputedExpectations() {
        DashboardView view = dashboardService.summarize();

        assertThat(view.totalLeases()).isEqualTo(30);
        assertThat(view.totalAnnualBaseRent()).isEqualByComparingTo("48320500");
        assertThat(view.unassignedLeases()).isEqualTo(8);
        assertThat(view.expiringWithin90Days()).isEqualTo(7);
        assertThat(view.expiringWithin180Days()).isEqualTo(13);
        assertThat(view.expiringWithin365Days()).isEqualTo(20);
        assertThat(view.overdueRenewalNotices()).isEqualTo(16);
        assertThat(view.highRiskCount() + view.mediumRiskCount() + view.lowRiskCount() + view.noneRiskCount())
                .isEqualTo(30);
    }

    @Test
    void leaseListFiltersByRiskLevelAndPaginates() {
        PagedResult<?> highRiskFirstPage = leaseListService.list(
                new LeaseFilters(null, null, null, null, null, RiskLevel.HIGH), 0, 5);

        assertThat(highRiskFirstPage.items()).hasSizeLessThanOrEqualTo(5);
        assertThat(highRiskFirstPage.totalItems()).isEqualTo(dashboardService.summarize().highRiskCount());

        PagedResult<?> unfiltered = leaseListService.list(LeaseFilters.none(), 0, 10);
        assertThat(unfiltered.totalItems()).isEqualTo(30);
        assertThat(unfiltered.items()).hasSize(10);
        assertThat(unfiltered.totalPages()).isEqualTo(3);
    }

    @Test
    void leaseListFiltersByCity() {
        PagedResult<?> austinLeases = leaseListService.list(
                new LeaseFilters(null, "Austin", null, null, null, null), 0, 50);

        assertThat(austinLeases.totalItems()).isEqualTo(4); // PROP-AUS-01 has 4 leases in the demo CSV
    }
}
