package com.leaseguard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Portfolio-wide metrics for the dashboard screen, computed fresh on every request from the
 * current lease data and the configured "as of" date.
 *
 * <p>{@code annualRentAtRisk} sums annual base rent across leases whose risk level is HIGH or
 * MEDIUM - the leases a manager actually needs to act on, as opposed to every dollar under
 * management. {@code expiringWithinNDays} counts are cumulative (0-90, 0-180, 0-365 days from
 * the as-of date) and exclude already-expired leases, which are surfaced separately via
 * {@code overdueRenewalNotices} and the HIGH-risk "expired" reason code.
 */
public record DashboardView(
        LocalDate asOfDate,
        long totalLeases,
        BigDecimal totalAnnualBaseRent,
        long expiringWithin90Days,
        long expiringWithin180Days,
        long expiringWithin365Days,
        BigDecimal annualRentAtRisk,
        long overdueRenewalNotices,
        long unassignedLeases,
        long highRiskCount,
        long mediumRiskCount,
        long lowRiskCount,
        long noneRiskCount,
        List<LeaseRiskView> topHighRiskLeases) {
}
