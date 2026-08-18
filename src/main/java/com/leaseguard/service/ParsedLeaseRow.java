package com.leaseguard.service;

import com.leaseguard.model.LeaseStatus;
import com.leaseguard.model.PropertyType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** A CSV row whose fields all passed type/format validation, ready for batch-level checks and import. */
public record ParsedLeaseRow(
        int rowNumber,
        String leaseExternalId,
        String propertyExternalId,
        String propertyName,
        PropertyType propertyType,
        String addressLine1,
        String city,
        String state,
        String postalCode,
        Integer totalRentableSqFt,
        String tenantExternalId,
        String tenantName,
        String tenantIndustry,
        Integer leasedSqFt,
        LocalDate leaseStartDate,
        LocalDate leaseEndDate,
        LocalDate renewalNoticeDate,
        BigDecimal annualBaseRent,
        LeaseStatus leaseStatus,
        String assignedManager,
        LocalDate lastContactDate) {
}
