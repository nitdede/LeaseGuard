package com.leaseguard.service;

import com.leaseguard.model.Lease;
import com.leaseguard.dto.DashboardView;
import com.leaseguard.dto.LeaseFilters;
import com.leaseguard.repository.LeaseRepository;
import com.leaseguard.dto.LeaseRiskView;
import com.leaseguard.repository.LeaseSpecifications;
import com.leaseguard.dto.RiskLevel;
import com.leaseguard.dto.RiskAssessment;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private static final int TOP_HIGH_RISK_LIMIT = 10;

    private final LeaseRepository leaseRepository;
    private final RiskScoreService riskScoreService;
    private final Clock clock;

    public DashboardService(LeaseRepository leaseRepository, RiskScoreService riskScoreService, Clock clock) {
        this.leaseRepository = leaseRepository;
        this.riskScoreService = riskScoreService;
        this.clock = clock;
    }

    /**
     * Builds everything the portfolio dashboard shows - the KPI numbers, the risk-level counts
     * for the chart, and the top high-risk leases table - by loading every lease and scoring it
     * fresh right now. Nothing here is cached or precomputed, so the result always reflects the
     * database as it currently stands; there is no "stale dashboard" case to worry about.
     *
     * <p>See {@link DashboardView} for what each returned field means, including a couple of
     * deliberate choices (e.g. rent-at-risk only counts HIGH/MEDIUM leases, and the
     * expiring-within-N-days counts exclude already-expired leases) that are easy to assume
     * differently if you're just reading the numbers.
     */
    @Transactional(readOnly = true)
    public DashboardView summarize() {
        LocalDate asOf = LocalDate.now(clock);

        // Load all leases and compute their risk scores, along with the total annual base rent for each property. This is done in a single query to avoid N+1 issues.
        List<Lease> leases = leaseRepository.findAll(LeaseSpecifications.from(LeaseFilters.none(), asOf));
        Map<Long, BigDecimal> propertyRentTotals = leaseRepository.sumAnnualBaseRentByProperty().stream()
                .collect(Collectors.toMap(LeaseRepository.PropertyRentTotal::getPropertyId,
                        LeaseRepository.PropertyRentTotal::getTotalAnnualBaseRent));

        List<LeaseRiskView> scored = leases.stream()
                .map(lease -> {
                    RiskAssessment assessment = riskScoreService.score(lease,
                            propertyRentTotals.get(lease.getProperty().getId()));
                    return LeaseRiskView.of(lease, assessment);
                })
                .toList();

        // Compute the total annual base rent across all leases.
        BigDecimal totalAnnualBaseRent = leases.stream()
                .map(Lease::getAnnualBaseRent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Compute the total annual base rent at risk, which is the sum of annual base rent for leases that are HIGH or MEDIUM risk.        
        BigDecimal annualRentAtRisk = scored.stream()
                .filter(view -> view.riskLevel() == RiskLevel.HIGH || view.riskLevel() == RiskLevel.MEDIUM)
                .map(LeaseRiskView::annualBaseRent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Count the number of leases with overdue renewal notices (renewal notice date is before asOf and lease is not renewed).        
        long overdueRenewalNotices = leases.stream()
                .filter(lease -> lease.getRenewalNoticeDate() != null
                        && lease.getRenewalNoticeDate().isBefore(asOf)
                        && lease.getStatus() != com.leaseguard.model.LeaseStatus.RENEWED)
                .count();

        // Count the number of leases that are unassigned (no assigned manager).        
        long unassignedLeases = leases.stream()
                .filter(lease -> lease.getAssignedManager() == null || lease.getAssignedManager().isBlank())
                .count();

        // Count the number of leases in each risk level (HIGH, MEDIUM, LOW, NONE).        
        Map<RiskLevel, Long> countsByLevel = new EnumMap<>(RiskLevel.class);
        for (RiskLevel level : RiskLevel.values()) {
            countsByLevel.put(level, 0L);
        }

        // Populate the countsByLevel map with the actual counts from the scored leases.
        scored.forEach(view -> countsByLevel.merge(view.riskLevel(), 1L, Long::sum));

        // Get the top HIGH risk leases, sorted by risk score descending, limited to TOP_HIGH_RISK_LIMIT.
        List<LeaseRiskView> topHighRisk = scored.stream()
                .filter(view -> view.riskLevel() == RiskLevel.HIGH)
                .sorted(Comparator.comparingInt(LeaseRiskView::riskScore).reversed())
                .limit(TOP_HIGH_RISK_LIMIT)
                .toList();

        return new DashboardView(
                asOf,
                leases.size(),
                totalAnnualBaseRent,
                expiringWithin(leases, asOf, 90),
                expiringWithin(leases, asOf, 180),
                expiringWithin(leases, asOf, 365),
                annualRentAtRisk,
                overdueRenewalNotices,
                unassignedLeases,
                countsByLevel.get(RiskLevel.HIGH),
                countsByLevel.get(RiskLevel.MEDIUM),
                countsByLevel.get(RiskLevel.LOW),
                countsByLevel.get(RiskLevel.NONE),
                topHighRisk);
    }

    // Count the number of leases that are expiring within the specified number of days from the given date.
    private long expiringWithin(List<Lease> leases, LocalDate asOf, int days) {
        return leases.stream()
                .filter(lease -> {
                    long daysToExpiration = ChronoUnit.DAYS.between(asOf, lease.getEndDate());
                    return daysToExpiration >= 0 && daysToExpiration <= days;
                })
                .count();
    }
}
