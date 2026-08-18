package com.leaseguard.dto;

import java.util.List;

/**
 * The outcome of scoring one lease: the numeric score, its risk-level band, and every
 * contributing reason code (in the order the rules were evaluated). Not persisted - always
 * computed on read from the lease's current data and the configured "as of" date.
 */
public record RiskAssessment(int score, RiskLevel level, List<RiskReasonCode> reasons) {

    public static RiskAssessment renewed() {
        return new RiskAssessment(0, RiskLevel.NONE, List.of(RiskReasonCode.RENEWED));
    }
}
