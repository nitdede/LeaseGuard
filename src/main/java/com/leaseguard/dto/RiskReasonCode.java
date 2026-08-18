package com.leaseguard.dto;

/** Machine-readable reason codes behind a risk score, each paired with a human-readable message. */
public enum RiskReasonCode {
    LEASE_EXPIRED("Lease expired and is not marked renewed", 50),
    EXPIRING_0_90_DAYS("Lease expires within 90 days", 40),
    EXPIRING_91_180_DAYS("Lease expires in 91-180 days", 25),
    EXPIRING_181_365_DAYS("Lease expires in 181-365 days", 10),
    RENEWAL_NOTICE_PASSED("Renewal-notice deadline has passed and lease is not marked renewed", 35),
    HIGH_ANNUAL_RENT("Annual base rent is at or above the high-rent threshold", 15),
    TENANT_RENT_CONCENTRATION("Tenant represents a large share of this property's rent", 10),
    NO_MANAGER_ASSIGNED("No manager is assigned to this lease", 10),
    RENEWAL_IN_PROGRESS("Renewal is already in progress", -20),
    RENEWED("Lease has been renewed", 0);

    private final String message;
    private final int points;

    RiskReasonCode(String message, int points) {
        this.message = message;
        this.points = points;
    }

    public String message() {
        return message;
    }

    public int points() {
        return points;
    }
}
