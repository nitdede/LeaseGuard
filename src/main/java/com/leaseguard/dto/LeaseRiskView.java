package com.leaseguard.dto;

import com.leaseguard.model.Lease;
import com.leaseguard.model.LeaseStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Read model combining a {@link Lease} with its computed {@link RiskAssessment}, used by the
 * dashboard, lease list, and lease detail screens. Never exposes the JPA entity directly.
 */
public record LeaseRiskView(
        Long id,
        String externalId,
        String propertyName,
        String city,
        String tenantName,
        LocalDate endDate,
        LocalDate renewalNoticeDate,
        BigDecimal annualBaseRent,
        LeaseStatus status,
        String assignedManager,
        long version,
        int riskScore,
        RiskLevel riskLevel,
        java.util.List<RiskReasonCode> riskReasons) {

    public static LeaseRiskView of(Lease lease, RiskAssessment assessment) {
        return new LeaseRiskView(
                lease.getId(),
                lease.getExternalId(),
                lease.getProperty().getName(),
                lease.getProperty().getCity(),
                lease.getTenant().getName(),
                lease.getEndDate(),
                lease.getRenewalNoticeDate(),
                lease.getAnnualBaseRent(),
                lease.getStatus(),
                lease.getAssignedManager(),
                lease.getVersion(),
                assessment.score(),
                assessment.level(),
                assessment.reasons());
    }
}
