package com.leaseguard.service;

import static com.leaseguard.service.LeaseCsvColumns.ADDRESS_LINE1;
import static com.leaseguard.service.LeaseCsvColumns.ANNUAL_BASE_RENT;
import static com.leaseguard.service.LeaseCsvColumns.ASSIGNED_MANAGER;
import static com.leaseguard.service.LeaseCsvColumns.CITY;
import static com.leaseguard.service.LeaseCsvColumns.LAST_CONTACT_DATE;
import static com.leaseguard.service.LeaseCsvColumns.LEASED_SQFT;
import static com.leaseguard.service.LeaseCsvColumns.LEASE_END_DATE;
import static com.leaseguard.service.LeaseCsvColumns.LEASE_EXTERNAL_ID;
import static com.leaseguard.service.LeaseCsvColumns.LEASE_START_DATE;
import static com.leaseguard.service.LeaseCsvColumns.LEASE_STATUS;
import static com.leaseguard.service.LeaseCsvColumns.POSTAL_CODE;
import static com.leaseguard.service.LeaseCsvColumns.PROPERTY_EXTERNAL_ID;
import static com.leaseguard.service.LeaseCsvColumns.PROPERTY_NAME;
import static com.leaseguard.service.LeaseCsvColumns.PROPERTY_TYPE;
import static com.leaseguard.service.LeaseCsvColumns.RENEWAL_NOTICE_DATE;
import static com.leaseguard.service.LeaseCsvColumns.STATE;
import static com.leaseguard.service.LeaseCsvColumns.TENANT_EXTERNAL_ID;
import static com.leaseguard.service.LeaseCsvColumns.TENANT_INDUSTRY;
import static com.leaseguard.service.LeaseCsvColumns.TENANT_NAME;
import static com.leaseguard.service.LeaseCsvColumns.TOTAL_RENTABLE_SQFT;

import com.leaseguard.dto.ImportIssue;
import com.leaseguard.model.LeaseStatus;
import com.leaseguard.model.PropertyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Validates and normalizes one raw CSV row against the rules in {@code docs/data-dictionary.md},
 * collecting every field error rather than stopping at the first one so the preview report is
 * complete in a single pass. Pure and stateless - safe to reuse across rows and easy to unit
 * test with in-memory {@link RawCsvRow} instances.
 */
@Component
class CsvRowValidator {

    RowValidationResult validate(RawCsvRow row, LocalDate asOfDate) {
        List<ImportIssue> errors = new ArrayList<>();

        String leaseExternalId = requireText(row, errors, LEASE_EXTERNAL_ID, 1, 64);
        String propertyExternalId = requireText(row, errors, PROPERTY_EXTERNAL_ID, 1, 64);
        String propertyName = requireText(row, errors, PROPERTY_NAME, 1, 150);
        PropertyType propertyType = requireEnum(row, errors, PROPERTY_TYPE, PropertyType.class);
        String addressLine1 = requireText(row, errors, ADDRESS_LINE1, 1, 200);
        String city = requireText(row, errors, CITY, 1, 100);
        String state = requireStateCode(row, errors);
        String postalCode = requireText(row, errors, POSTAL_CODE, 1, 10);
        Integer totalRentableSqFt = requirePositiveInt(row, errors, TOTAL_RENTABLE_SQFT);
        String tenantExternalId = requireText(row, errors, TENANT_EXTERNAL_ID, 1, 64);
        String tenantName = requireText(row, errors, TENANT_NAME, 1, 150);
        String tenantIndustry = row.get(TENANT_INDUSTRY);
        Integer leasedSqFt = requirePositiveInt(row, errors, LEASED_SQFT);
        LocalDate leaseStartDate = requireDate(row, errors, LEASE_START_DATE);
        LocalDate leaseEndDate = requireDate(row, errors, LEASE_END_DATE);
        LocalDate renewalNoticeDate = optionalDate(row, errors, RENEWAL_NOTICE_DATE);
        BigDecimal annualBaseRent = requireNonNegativeDecimal(row, errors, ANNUAL_BASE_RENT);
        LeaseStatus leaseStatus = requireEnum(row, errors, LEASE_STATUS, LeaseStatus.class);
        String assignedManager = row.get(ASSIGNED_MANAGER);
        LocalDate lastContactDate = optionalDate(row, errors, LAST_CONTACT_DATE);

        if (leasedSqFt != null && totalRentableSqFt != null && leasedSqFt > totalRentableSqFt) {
            errors.add(ImportIssue.error(row.rowNumber(), LEASED_SQFT, String.valueOf(leasedSqFt),
                    "Leased square footage cannot exceed the property's total rentable square footage ("
                            + totalRentableSqFt + ")."));
        }
        if (leaseStartDate != null && leaseEndDate != null && !leaseEndDate.isAfter(leaseStartDate)) {
            errors.add(ImportIssue.error(row.rowNumber(), LEASE_END_DATE, String.valueOf(leaseEndDate),
                    "Lease end date must be after the start date."));
        }
        if (renewalNoticeDate != null && leaseEndDate != null && renewalNoticeDate.isAfter(leaseEndDate)) {
            errors.add(ImportIssue.error(row.rowNumber(), RENEWAL_NOTICE_DATE, String.valueOf(renewalNoticeDate),
                    "Renewal notice date must not be later than the lease end date."));
        }
        if (lastContactDate != null && lastContactDate.isAfter(asOfDate)) {
            errors.add(ImportIssue.error(row.rowNumber(), LAST_CONTACT_DATE, String.valueOf(lastContactDate),
                    "Last contact date cannot be in the future."));
        }

        if (!errors.isEmpty()) {
            return new RowValidationResult(row.rowNumber(), null, errors);
        }

        ParsedLeaseRow parsed = new ParsedLeaseRow(row.rowNumber(), leaseExternalId, propertyExternalId,
                propertyName, propertyType, addressLine1, city, state, postalCode, totalRentableSqFt,
                tenantExternalId, tenantName, tenantIndustry, leasedSqFt, leaseStartDate, leaseEndDate,
                renewalNoticeDate, annualBaseRent, leaseStatus, assignedManager, lastContactDate);
        return new RowValidationResult(row.rowNumber(), parsed, List.of());
    }

    // Require a text field to be present and within the specified length range.
    private String requireText(RawCsvRow row, List<ImportIssue> errors, String field, int minLen, int maxLen) {
        String value = row.get(field);
        if (value == null) {
            errors.add(ImportIssue.error(row.rowNumber(), field, null, field + " is required."));
            return null;
        }
        if (value.length() < minLen || value.length() > maxLen) {
            errors.add(ImportIssue.error(row.rowNumber(), field, value,
                    field + " must be between " + minLen + " and " + maxLen + " characters."));
            return null;
        }
        return value;
    }

    // Require a valid two-letter US state code.
    private String requireStateCode(RawCsvRow row, List<ImportIssue> errors) {
        String value = row.get(STATE);
        if (value == null) {
            errors.add(ImportIssue.error(row.rowNumber(), STATE, null, STATE + " is required."));
            return null;
        }
        if (!value.matches("[A-Za-z]{2}")) {
            errors.add(ImportIssue.error(row.rowNumber(), STATE, value, STATE + " must be a two-letter US state code."));
            return null;
        }
        return value.toUpperCase(Locale.ROOT);
    }

    // Require a positive integer value for the specified field.
    private Integer requirePositiveInt(RawCsvRow row, List<ImportIssue> errors, String field) {
        String value = row.get(field);
        if (value == null) {
            errors.add(ImportIssue.error(row.rowNumber(), field, null, field + " is required."));
            return null;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                errors.add(ImportIssue.error(row.rowNumber(), field, value, field + " must be a positive integer."));
                return null;
            }
            return parsed;
        } catch (NumberFormatException e) {
            errors.add(ImportIssue.error(row.rowNumber(), field, value, field + " must be a valid integer."));
            return null;
        }
    }

    // Require a valid date in yyyy-MM-dd format for the specified field.
    private LocalDate requireDate(RawCsvRow row, List<ImportIssue> errors, String field) {
        String value = row.get(field);
        if (value == null) {
            errors.add(ImportIssue.error(row.rowNumber(), field, null, field + " is required."));
            return null;
        }
        return parseDate(row, errors, field, value);
    }

    private LocalDate optionalDate(RawCsvRow row, List<ImportIssue> errors, String field) {
        String value = row.get(field);
        if (value == null) {
            return null;
        }
        return parseDate(row, errors, field, value);
    }

    // Parse a date string in yyyy-MM-dd format, adding an error if it is invalid.
    private LocalDate parseDate(RawCsvRow row, List<ImportIssue> errors, String field, String value) {
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            errors.add(ImportIssue.error(row.rowNumber(), field, value, field + " must be a valid date in yyyy-MM-dd format."));
            return null;
        }
    }

    // Require a non-negative decimal value for the specified field.
    private BigDecimal requireNonNegativeDecimal(RawCsvRow row, List<ImportIssue> errors, String field) {
        String value = row.get(field);
        if (value == null) {
            errors.add(ImportIssue.error(row.rowNumber(), field, null, field + " is required."));
            return null;
        }
        try {
            BigDecimal parsed = new BigDecimal(value);
            if (parsed.signum() < 0) {
                errors.add(ImportIssue.error(row.rowNumber(), field, value, field + " must not be negative."));
                return null;
            }
            return parsed;
        } catch (NumberFormatException e) {
            errors.add(ImportIssue.error(row.rowNumber(), field, value, field + " must be a valid decimal amount."));
            return null;
        }
    }

    // Require a valid enum value for the specified field.
    private <E extends Enum<E>> E requireEnum(RawCsvRow row, List<ImportIssue> errors, String field, Class<E> enumClass) {
        String value = row.get(field);
        if (value == null) {
            errors.add(ImportIssue.error(row.rowNumber(), field, null, field + " is required."));
            return null;
        }
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            errors.add(ImportIssue.error(row.rowNumber(), field, value,
                    field + " must be one of: " + String.join(", ", enumNames(enumClass)) + "."));
            return null;
        }
    }
    // Retrieve the names of all constants in the specified enum class.
    private <E extends Enum<E>> List<String> enumNames(Class<E> enumClass) {
        return Arrays.stream(enumClass.getEnumConstants()).map(Enum::name).toList();
    }
}
