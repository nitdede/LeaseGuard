package com.leaseguard.dto;

import com.leaseguard.model.LeaseStatus;

/**
 * Database-level filter criteria for the lease list screen. Risk-level filtering is applied
 * afterward in {@code LeaseListService} because the risk score is computed, not stored - see
 * that class for why filtering/sorting/pagination happen in memory for this MVP's data scale.
 */
public record LeaseFilters(
        Long propertyId,
        String city,
        LeaseStatus status,
        String assignedManager,
        Integer expiringWithinDays,
        RiskLevel riskLevel) {

    public static LeaseFilters none() {
        return new LeaseFilters(null, null, null, null, null, null);
    }
}
