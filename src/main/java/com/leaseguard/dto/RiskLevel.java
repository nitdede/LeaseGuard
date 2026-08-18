package com.leaseguard.dto;

/**
 * Risk-level bands derived from the numeric score. Thresholds: HIGH 70+, MEDIUM 40-69,
 * LOW 1-39, NONE 0. See {@link RiskScoreService} for how the score is computed.
 */
public enum RiskLevel {
    HIGH,
    MEDIUM,
    LOW,
    NONE;

    public static RiskLevel fromScore(int score) {
        if (score >= 70) {
            return HIGH;
        }
        if (score >= 40) {
            return MEDIUM;
        }
        if (score >= 1) {
            return LOW;
        }
        return NONE;
    }
}
