package com.leaseguard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.leaseguard.dto.IssueSeverity;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CsvRowValidatorTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 8, 17);

    private final CsvRowValidator validator = new CsvRowValidator();

    @Test
    void validRowParsesAndNormalizesFields() {
        RawCsvRow row = validRow(Map.of());

        RowValidationResult result = validator.validate(row, AS_OF);

        assertThat(result.isValid()).isTrue();
        assertThat(result.parsedRow().state()).isEqualTo("TX"); // normalized to uppercase
        assertThat(result.parsedRow().tenantIndustry()).isNull(); // blank optional normalizes to null
    }

    @Test
    void missingRequiredFieldProducesFieldError() {
        RawCsvRow row = validRow(Map.of(LeaseCsvColumns.LEASE_EXTERNAL_ID, ""));

        RowValidationResult result = validator.validate(row, AS_OF);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anySatisfy(issue -> {
            assertThat(issue.fieldName()).isEqualTo(LeaseCsvColumns.LEASE_EXTERNAL_ID);
            assertThat(issue.severity()).isEqualTo(IssueSeverity.ERROR);
        });
    }

    @Test
    void invalidPropertyTypeEnumProducesFieldError() {
        RawCsvRow row = validRow(Map.of(LeaseCsvColumns.PROPERTY_TYPE, "HOTEL"));

        RowValidationResult result = validator.validate(row, AS_OF);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anySatisfy(issue -> assertThat(issue.fieldName()).isEqualTo(LeaseCsvColumns.PROPERTY_TYPE));
    }

    @Test
    void invalidLeaseStatusEnumProducesFieldError() {
        RawCsvRow row = validRow(Map.of(LeaseCsvColumns.LEASE_STATUS, "ACTIVE"));

        RowValidationResult result = validator.validate(row, AS_OF);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anySatisfy(issue -> assertThat(issue.fieldName()).isEqualTo(LeaseCsvColumns.LEASE_STATUS));
    }

    @Test
    void malformedDateProducesFieldError() {
        RawCsvRow row = validRow(Map.of(LeaseCsvColumns.LEASE_START_DATE, "not-a-date"));

        RowValidationResult result = validator.validate(row, AS_OF);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anySatisfy(issue -> assertThat(issue.fieldName()).isEqualTo(LeaseCsvColumns.LEASE_START_DATE));
    }

    @Test
    void negativeAnnualBaseRentProducesFieldError() {
        RawCsvRow row = validRow(Map.of(LeaseCsvColumns.ANNUAL_BASE_RENT, "-250000"));

        RowValidationResult result = validator.validate(row, AS_OF);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anySatisfy(issue -> assertThat(issue.fieldName()).isEqualTo(LeaseCsvColumns.ANNUAL_BASE_RENT));
    }

    @Test
    void nonPositiveSquareFootageProducesFieldError() {
        RawCsvRow row = validRow(Map.of(LeaseCsvColumns.LEASED_SQFT, "0"));

        RowValidationResult result = validator.validate(row, AS_OF);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anySatisfy(issue -> assertThat(issue.fieldName()).isEqualTo(LeaseCsvColumns.LEASED_SQFT));
    }

    @Test
    void leasedSquareFootageExceedingPropertyTotalProducesFieldError() {
        RawCsvRow row = validRow(Map.of(
                LeaseCsvColumns.TOTAL_RENTABLE_SQFT, "1000",
                LeaseCsvColumns.LEASED_SQFT, "5000"));

        RowValidationResult result = validator.validate(row, AS_OF);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anySatisfy(issue -> assertThat(issue.fieldName()).isEqualTo(LeaseCsvColumns.LEASED_SQFT));
    }

    @Test
    void endDateNotAfterStartDateProducesFieldError() {
        RawCsvRow row = validRow(Map.of(
                LeaseCsvColumns.LEASE_START_DATE, "2027-01-01",
                LeaseCsvColumns.LEASE_END_DATE, "2026-01-01"));

        RowValidationResult result = validator.validate(row, AS_OF);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anySatisfy(issue -> assertThat(issue.fieldName()).isEqualTo(LeaseCsvColumns.LEASE_END_DATE));
    }

    @Test
    void renewalNoticeDateAfterEndDateProducesFieldError() {
        RawCsvRow row = validRow(Map.of(LeaseCsvColumns.RENEWAL_NOTICE_DATE, "2030-01-01"));

        RowValidationResult result = validator.validate(row, AS_OF);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anySatisfy(issue -> assertThat(issue.fieldName()).isEqualTo(LeaseCsvColumns.RENEWAL_NOTICE_DATE));
    }

    @Test
    void lastContactDateInFutureProducesFieldError() {
        RawCsvRow row = validRow(Map.of(LeaseCsvColumns.LAST_CONTACT_DATE, "2099-01-01"));

        RowValidationResult result = validator.validate(row, AS_OF);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anySatisfy(issue -> assertThat(issue.fieldName()).isEqualTo(LeaseCsvColumns.LAST_CONTACT_DATE));
    }

    @Test
    void invalidStateCodeProducesFieldError() {
        RawCsvRow row = validRow(Map.of(LeaseCsvColumns.STATE, "Texas"));

        RowValidationResult result = validator.validate(row, AS_OF);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anySatisfy(issue -> assertThat(issue.fieldName()).isEqualTo(LeaseCsvColumns.STATE));
    }

    @Test
    void multipleInvalidFieldsAreAllReportedInOnePass() {
        RawCsvRow row = validRow(Map.of(
                LeaseCsvColumns.ANNUAL_BASE_RENT, "-1",
                LeaseCsvColumns.LEASED_SQFT, "-1"));

        RowValidationResult result = validator.validate(row, AS_OF);

        assertThat(result.errors()).hasSize(2);
    }

    private RawCsvRow validRow(Map<String, String> overrides) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(LeaseCsvColumns.LEASE_EXTERNAL_ID, "LSE-1001");
        fields.put(LeaseCsvColumns.PROPERTY_EXTERNAL_ID, "PROP-DAL-01");
        fields.put(LeaseCsvColumns.PROPERTY_NAME, "Las Colinas Commerce Center");
        fields.put(LeaseCsvColumns.PROPERTY_TYPE, "OFFICE");
        fields.put(LeaseCsvColumns.ADDRESS_LINE1, "500 Riverside Drive");
        fields.put(LeaseCsvColumns.CITY, "Irving");
        fields.put(LeaseCsvColumns.STATE, "tx");
        fields.put(LeaseCsvColumns.POSTAL_CODE, "75039");
        fields.put(LeaseCsvColumns.TOTAL_RENTABLE_SQFT, "220000");
        fields.put(LeaseCsvColumns.TENANT_EXTERNAL_ID, "TEN-001");
        fields.put(LeaseCsvColumns.TENANT_NAME, "NorthStar Financial");
        fields.put(LeaseCsvColumns.TENANT_INDUSTRY, "");
        fields.put(LeaseCsvColumns.LEASED_SQFT, "42000");
        fields.put(LeaseCsvColumns.LEASE_START_DATE, "2021-10-01");
        fields.put(LeaseCsvColumns.LEASE_END_DATE, "2026-10-31");
        fields.put(LeaseCsvColumns.RENEWAL_NOTICE_DATE, "2026-04-30");
        fields.put(LeaseCsvColumns.ANNUAL_BASE_RENT, "1512000");
        fields.put(LeaseCsvColumns.LEASE_STATUS, "NOT_STARTED");
        fields.put(LeaseCsvColumns.ASSIGNED_MANAGER, "");
        fields.put(LeaseCsvColumns.LAST_CONTACT_DATE, "");
        fields.putAll(overrides);
        return new RawCsvRow(2, fields);
    }
}
