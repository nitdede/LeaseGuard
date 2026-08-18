package com.leaseguard.service;

import com.leaseguard.dto.LeaseFilters;
import com.leaseguard.dto.LeaseRiskView;
import com.leaseguard.dto.PagedResult;
import com.leaseguard.exception.LeaseNotFoundException;
import com.leaseguard.model.Lease;
import com.leaseguard.repository.LeaseRepository;
import com.leaseguard.repository.LeaseSpecifications;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backs the lease list screen. Property/city/status/manager/expiration filters run in SQL via
 * {@link LeaseSpecifications}; risk level cannot (it is computed, not stored), so it is applied
 * after scoring, followed by descending-score sort and in-memory pagination. This is intentional
 * for the MVP's data scale (tens of thousands of leases at most) - see
 * {@code docs/architecture.md} for when a materialized risk view would replace it.
 */
@Service
public class LeaseListService {

    private final LeaseRepository leaseRepository;
    private final RiskScoreService riskScoreService;
    private final Clock clock;

    public LeaseListService(LeaseRepository leaseRepository, RiskScoreService riskScoreService, Clock clock) {
        this.leaseRepository = leaseRepository;
        this.riskScoreService = riskScoreService;
        this.clock = clock;
    }

    // Returns a paginated list of leases matching the given filters, with their risk scores computed. The risk level filter is applied after scoring, and the results are sorted by descending risk score.
    @Transactional(readOnly = true)
    public PagedResult<LeaseRiskView> list(LeaseFilters filters, int page, int pageSize) {
        LocalDate asOf = LocalDate.now(clock);
        // Load all leases matching the filters and compute their risk scores, along with the total annual base rent for each property. This is done in a single query to avoid N+1 issues.
        List<Lease> leases = leaseRepository.findAll(LeaseSpecifications.from(filters, asOf));

        // Compute the total annual base rent for each property across all leases, used for risk scoring.
        Map<Long, BigDecimal> propertyRentTotals = leaseRepository.sumAnnualBaseRentByProperty().stream()
                .collect(Collectors.toMap(LeaseRepository.PropertyRentTotal::getPropertyId,
                        LeaseRepository.PropertyRentTotal::getTotalAnnualBaseRent));

        // Compute the risk score for each lease and create a LeaseRiskView for each, filtering by risk level if specified, and sorting by descending risk score.                
        List<LeaseRiskView> scored = leases.stream()
                .map(lease -> LeaseRiskView.of(lease,
                        riskScoreService.score(lease, propertyRentTotals.get(lease.getProperty().getId()))))
                .filter(view -> filters.riskLevel() == null || view.riskLevel() == filters.riskLevel())
                .sorted(Comparator.comparingInt(LeaseRiskView::riskScore).reversed())
                .toList();

        int totalItems = scored.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) pageSize));
        int fromIndex = Math.min(page * pageSize, totalItems);
        int toIndex = Math.min(fromIndex + pageSize, totalItems);

        return new PagedResult<>(scored.subList(fromIndex, toIndex), page, pageSize, totalItems, totalPages);
    }


    // Returns the details of a specific lease, including its risk score, by loading the lease and computing its risk score based on the total annual base rent of the property it belongs to.
    @Transactional(readOnly = true)
    public LeaseRiskView detail(Long leaseId) {
        Lease lease = leaseRepository.findById(leaseId).orElseThrow(() -> new LeaseNotFoundException(leaseId));
        BigDecimal propertyTotal = leaseRepository.sumAnnualBaseRentByProperty().stream()
                .filter(t -> t.getPropertyId().equals(lease.getProperty().getId()))
                .map(LeaseRepository.PropertyRentTotal::getTotalAnnualBaseRent)
                .findFirst()
                .orElse(lease.getAnnualBaseRent());
        return LeaseRiskView.of(lease, riskScoreService.score(lease, propertyTotal));
    }
}
