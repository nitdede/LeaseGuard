package com.leaseguard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.leaseguard.config.RiskScoringProperties;
import com.leaseguard.dto.RiskAssessment;
import com.leaseguard.dto.RiskLevel;
import com.leaseguard.dto.RiskReasonCode;
import com.leaseguard.model.Lease;
import com.leaseguard.model.LeaseStatus;
import com.leaseguard.model.Property;
import com.leaseguard.model.PropertyType;
import com.leaseguard.model.Tenant;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RiskScoreServiceTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 8, 17);
    private static final Clock CLOCK = Clock.fixed(AS_OF.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
    private static final BigDecimal HIGH_RENT_THRESHOLD = BigDecimal.valueOf(1_000_000);
    private static final BigDecimal CONCENTRATION_THRESHOLD_PERCENT = BigDecimal.valueOf(25);
    private static final BigDecimal LOW_RENT = BigDecimal.valueOf(100);
    private static final BigDecimal LARGE_PROPERTY_TOTAL = BigDecimal.valueOf(1_000_000);

    private final RiskScoreService service = new RiskScoreService(CLOCK,
            new RiskScoringProperties(HIGH_RENT_THRESHOLD, CONCENTRATION_THRESHOLD_PERCENT));

    @ParameterizedTest(name = "endDate {0} days from as-of -> score {1}, reason {2}")
    @CsvSource({
            "-1, 50, LEASE_EXPIRED",
            "0, 40, EXPIRING_0_90_DAYS",
            "90, 40, EXPIRING_0_90_DAYS",
            "91, 25, EXPIRING_91_180_DAYS",
            "180, 25, EXPIRING_91_180_DAYS",
            "181, 10, EXPIRING_181_365_DAYS",
            "365, 10, EXPIRING_181_365_DAYS",
            "366, 0, NONE"
    })
    void expirationBucketBoundaries(int daysFromAsOf, int expectedScore, String expectedReason) {
        Lease lease = baseline(AS_OF.plusDays(daysFromAsOf));

        RiskAssessment assessment = service.score(lease, LARGE_PROPERTY_TOTAL);

        assertThat(assessment.score()).isEqualTo(expectedScore);
        if ("NONE".equals(expectedReason)) {
            assertThat(assessment.reasons()).isEmpty();
        } else {
            assertThat(assessment.reasons()).containsExactly(RiskReasonCode.valueOf(expectedReason));
        }
    }

    @Test
    void renewalNoticePassedAddsThirtyFivePoints() {
        Lease lease = baseline(AS_OF.plusDays(400)); // outside every expiration bucket
        lease = withRenewalNotice(lease, AS_OF.minusDays(1));

        RiskAssessment assessment = service.score(lease, LARGE_PROPERTY_TOTAL);

        assertThat(assessment.score()).isEqualTo(35);
        assertThat(assessment.reasons()).containsExactly(RiskReasonCode.RENEWAL_NOTICE_PASSED);
    }

    @Test
    void renewalNoticeDueTodayDoesNotCountAsPassed() {
        Lease lease = baseline(AS_OF.plusDays(400));
        lease = withRenewalNotice(lease, AS_OF);

        RiskAssessment assessment = service.score(lease, LARGE_PROPERTY_TOTAL);

        assertThat(assessment.reasons()).doesNotContain(RiskReasonCode.RENEWAL_NOTICE_PASSED);
    }

    @Test
    void annualRentAtThresholdTriggersHighRentRule() {
        Lease atThreshold = leaseWithRent(HIGH_RENT_THRESHOLD);
        Lease belowThreshold = leaseWithRent(HIGH_RENT_THRESHOLD.subtract(BigDecimal.ONE));

        assertThat(service.score(atThreshold, LARGE_PROPERTY_TOTAL).reasons())
                .contains(RiskReasonCode.HIGH_ANNUAL_RENT);
        assertThat(service.score(belowThreshold, LARGE_PROPERTY_TOTAL).reasons())
                .doesNotContain(RiskReasonCode.HIGH_ANNUAL_RENT);
    }

    @Test
    void tenantRentConcentrationAtThresholdTriggers() {
        Lease lease = leaseWithRent(BigDecimal.valueOf(250)); // 25% of 1000 total
        BigDecimal propertyTotal = BigDecimal.valueOf(1000);

        RiskAssessment atThreshold = service.score(lease, propertyTotal);
        RiskAssessment belowThreshold = service.score(leaseWithRent(BigDecimal.valueOf(249)), propertyTotal);

        assertThat(atThreshold.reasons()).contains(RiskReasonCode.TENANT_RENT_CONCENTRATION);
        assertThat(belowThreshold.reasons()).doesNotContain(RiskReasonCode.TENANT_RENT_CONCENTRATION);
    }

    @Test
    void noManagerAssignedAddsTenPoints() {
        Lease unassigned = baseline(AS_OF.plusDays(400), null);
        Lease assigned = baseline(AS_OF.plusDays(400), "Priya Shah");

        assertThat(service.score(unassigned, LARGE_PROPERTY_TOTAL).reasons())
                .contains(RiskReasonCode.NO_MANAGER_ASSIGNED);
        assertThat(service.score(assigned, LARGE_PROPERTY_TOTAL).reasons())
                .doesNotContain(RiskReasonCode.NO_MANAGER_ASSIGNED);
    }

    @Test
    void renewalInProgressDeductsTwentyPoints() {
        Lease lease = withStatus(baseline(AS_OF.minusDays(1)), LeaseStatus.RENEWAL_IN_PROGRESS); // expired + in progress

        RiskAssessment assessment = service.score(lease, LARGE_PROPERTY_TOTAL);

        assertThat(assessment.score()).isEqualTo(30); // 50 - 20
        assertThat(assessment.reasons()).containsExactly(RiskReasonCode.LEASE_EXPIRED, RiskReasonCode.RENEWAL_IN_PROGRESS);
    }

    @Test
    void scoreFloorsAtZeroRatherThanGoingNegative() {
        Lease lease = withStatus(baseline(AS_OF.plusDays(400)), LeaseStatus.RENEWAL_IN_PROGRESS);

        RiskAssessment assessment = service.score(lease, LARGE_PROPERTY_TOTAL);

        assertThat(assessment.score()).isZero();
        assertThat(assessment.level()).isEqualTo(RiskLevel.NONE);
    }

    @Test
    void renewedStatusShortCircuitsToZeroRegardlessOfOtherFactors() {
        Lease lease = withStatus(withRenewalNotice(leaseWithRent(BigDecimal.valueOf(5_000_000)), AS_OF.minusDays(10)),
                LeaseStatus.RENEWED);
        // also expired, to prove every other rule is skipped
        lease = withEndDate(lease, AS_OF.minusDays(5));

        RiskAssessment assessment = service.score(lease, LARGE_PROPERTY_TOTAL);

        assertThat(assessment.score()).isZero();
        assertThat(assessment.level()).isEqualTo(RiskLevel.NONE);
        assertThat(assessment.reasons()).containsExactly(RiskReasonCode.RENEWED);
    }

    @Test
    void combinedRulesAccumulateAndProduceHighRiskLevel() {
        // Expired (+50) + notice passed (+35) + high rent (+15) + concentrated (+10) = 110 -> HIGH
        Property property = property();
        Tenant tenant = tenant();
        Lease lease = new Lease("LSE-COMBINED", property, tenant, 1000, LocalDate.of(2022, 1, 1),
                AS_OF.minusDays(12), AS_OF.minusDays(20), BigDecimal.valueOf(2_800_000),
                LeaseStatus.TENANT_EXITING, "Sofia Patel", null);

        RiskAssessment assessment = service.score(lease, BigDecimal.valueOf(7_795_000));

        assertThat(assessment.score()).isEqualTo(110);
        assertThat(assessment.level()).isEqualTo(RiskLevel.HIGH);
        assertThat(assessment.reasons()).containsExactlyInAnyOrder(
                RiskReasonCode.LEASE_EXPIRED, RiskReasonCode.RENEWAL_NOTICE_PASSED,
                RiskReasonCode.HIGH_ANNUAL_RENT, RiskReasonCode.TENANT_RENT_CONCENTRATION);
    }

    @ParameterizedTest(name = "score {0} -> level {1}")
    @CsvSource({"0, NONE", "1, LOW", "39, LOW", "40, MEDIUM", "69, MEDIUM", "70, HIGH", "110, HIGH"})
    void riskLevelThresholds(int score, String expectedLevel) {
        assertThat(RiskLevel.fromScore(score)).isEqualTo(RiskLevel.valueOf(expectedLevel));
    }

    private Lease baseline(LocalDate endDate) {
        return baseline(endDate, "Mgr");
    }

    private Lease baseline(LocalDate endDate, String assignedManager) {
        return new Lease("LSE-TEST", property(), tenant(), 1000, endDate.minusYears(2), endDate, null,
                LOW_RENT, LeaseStatus.MONITOR, assignedManager, null);
    }

    private Lease leaseWithRent(BigDecimal rent) {
        return new Lease("LSE-TEST", property(), tenant(), 1000, AS_OF.minusYears(2), AS_OF.plusDays(400), null,
                rent, LeaseStatus.MONITOR, "Mgr", null);
    }

    private Lease withRenewalNotice(Lease lease, LocalDate renewalNoticeDate) {
        return new Lease(lease.getExternalId(), lease.getProperty(), lease.getTenant(), lease.getLeasedSqFt(),
                lease.getStartDate(), lease.getEndDate(), renewalNoticeDate, lease.getAnnualBaseRent(),
                lease.getStatus(), lease.getAssignedManager(), lease.getLastContactDate());
    }

    private Lease withStatus(Lease lease, LeaseStatus status) {
        return new Lease(lease.getExternalId(), lease.getProperty(), lease.getTenant(), lease.getLeasedSqFt(),
                lease.getStartDate(), lease.getEndDate(), lease.getRenewalNoticeDate(), lease.getAnnualBaseRent(),
                status, lease.getAssignedManager(), lease.getLastContactDate());
    }

    private Lease withEndDate(Lease lease, LocalDate endDate) {
        return new Lease(lease.getExternalId(), lease.getProperty(), lease.getTenant(), lease.getLeasedSqFt(),
                lease.getStartDate(), endDate, lease.getRenewalNoticeDate(), lease.getAnnualBaseRent(),
                lease.getStatus(), lease.getAssignedManager(), lease.getLastContactDate());
    }

    private Property property() {
        return new Property("PROP-TEST", "Test Tower", PropertyType.OFFICE, "1 Test St", "Testville", "TX",
                "75001", 100_000);
    }

    private Tenant tenant() {
        return new Tenant("TEN-TEST", "Test Tenant Inc", "Technology");
    }
}
