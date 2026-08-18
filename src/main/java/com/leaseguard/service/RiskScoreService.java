package com.leaseguard.service;

import com.leaseguard.config.RiskScoringProperties;
import com.leaseguard.dto.RiskAssessment;
import com.leaseguard.dto.RiskLevel;
import com.leaseguard.dto.RiskReasonCode;
import com.leaseguard.model.Lease;
import com.leaseguard.model.LeaseStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Computes a deterministic, explainable risk score for a single lease.
 *
 * <p>Inputs: the {@link Lease} itself, plus the sum of annual base rent across every lease at
 * the same property (used for the rent-concentration rule), and the configured "as of" date
 * from the injected {@link Clock}.
 *
 * <p>Output: a {@link RiskAssessment} with the numeric score, its {@link RiskLevel} band, and
 * the ordered list of {@link RiskReasonCode}s that contributed to it. A lease with
 * {@link LeaseStatus#RENEWED} always scores exactly 0 with only the {@code RENEWED} reason;
 * every other rule is skipped in that case.
 *
 * <p>Expiration-bucket boundaries are inclusive of the day count from the as-of date to the
 * lease end date: negative (already past) is "expired"; 0-90 days is the most urgent open
 * bucket; 91-180 and 181-365 follow; 366+ days contributes nothing. The final score is floored
 * at 0 (the {@code RENEWAL_IN_PROGRESS} deduction cannot make it negative).
 */
@Service
public class RiskScoreService {

    private final Clock clock;
    private final RiskScoringProperties riskScoringProperties;

    public RiskScoreService(Clock clock, RiskScoringProperties riskScoringProperties) {
        this.clock = clock;
        this.riskScoringProperties = riskScoringProperties;
    }

    // Computes the risk score for a given lease, based on its attributes and the total annual base rent of the property it belongs to. Returns a RiskAssessment containing the score, risk level, and reasons for the score.
    public RiskAssessment score(Lease lease, BigDecimal propertyTotalAnnualBaseRent) {
        if (lease.getStatus() == LeaseStatus.RENEWED) {
            return RiskAssessment.renewed();
        }

        LocalDate asOf = LocalDate.now(clock);
        List<RiskReasonCode> reasons = new ArrayList<>();
        int score = 0;

        score += scoreExpirationBucket(lease.getEndDate(), asOf, reasons);

        if (lease.getRenewalNoticeDate() != null && lease.getRenewalNoticeDate().isBefore(asOf)) {
            score += add(RiskReasonCode.RENEWAL_NOTICE_PASSED, reasons);
        }

        if (lease.getAnnualBaseRent().compareTo(riskScoringProperties.highRentThresholdUsd()) >= 0) {
            score += add(RiskReasonCode.HIGH_ANNUAL_RENT, reasons);
        }

        if (isRentConcentrated(lease, propertyTotalAnnualBaseRent)) {
            score += add(RiskReasonCode.TENANT_RENT_CONCENTRATION, reasons);
        }

        if (lease.getAssignedManager() == null || lease.getAssignedManager().isBlank()) {
            score += add(RiskReasonCode.NO_MANAGER_ASSIGNED, reasons);
        }

        if (lease.getStatus() == LeaseStatus.RENEWAL_IN_PROGRESS) {
            score += add(RiskReasonCode.RENEWAL_IN_PROGRESS, reasons);
        }

        score = Math.max(0, score);
        return new RiskAssessment(score, RiskLevel.fromScore(score), List.copyOf(reasons));
    }

    private int scoreExpirationBucket(LocalDate endDate, LocalDate asOf, List<RiskReasonCode> reasons) {
        long daysToExpiration = ChronoUnit.DAYS.between(asOf, endDate);
        if (daysToExpiration < 0) {
            return add(RiskReasonCode.LEASE_EXPIRED, reasons);
        }
        if (daysToExpiration <= 90) {
            return add(RiskReasonCode.EXPIRING_0_90_DAYS, reasons);
        }
        if (daysToExpiration <= 180) {
            return add(RiskReasonCode.EXPIRING_91_180_DAYS, reasons);
        }
        if (daysToExpiration <= 365) {
            return add(RiskReasonCode.EXPIRING_181_365_DAYS, reasons);
        }
        return 0;
    }

    // Checks if the lease's annual base rent is concentrated relative to the total annual base rent of the property. 
    // Returns true if the lease's share of the total rent exceeds the configured tenant concentration threshold percentage; otherwise, returns false.
    private boolean isRentConcentrated(Lease lease, BigDecimal propertyTotalAnnualBaseRent) {
        if (propertyTotalAnnualBaseRent == null || propertyTotalAnnualBaseRent.signum() <= 0) {
            return false;
        }
        BigDecimal sharePercent = lease.getAnnualBaseRent()
                .multiply(BigDecimal.valueOf(100))
                .divide(propertyTotalAnnualBaseRent, 4, RoundingMode.HALF_UP);
        return sharePercent.compareTo(riskScoringProperties.tenantConcentrationThresholdPercent()) >= 0;
    }

    private static int add(RiskReasonCode code, List<RiskReasonCode> reasons) {
        reasons.add(code);
        return code.points();
    }
}
