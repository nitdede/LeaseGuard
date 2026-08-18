package com.leaseguard.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Configurable thresholds used by the risk-scoring rules. See
 * {@code docs/interview-defense.md} for why these are configuration-driven rather than
 * hardcoded: reviewers can tune sensitivity per portfolio without a code change.
 */
@Validated
@ConfigurationProperties(prefix = "leaseguard.risk")
public record RiskScoringProperties(
        @NotNull @Positive BigDecimal highRentThresholdUsd,
        @NotNull @Positive BigDecimal tenantConcentrationThresholdPercent) {
}
