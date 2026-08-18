package com.leaseguard.service;

import java.util.List;

/** Column names from {@code docs/data-dictionary.md}, in the order the demo CSVs use them. */
final class LeaseCsvColumns {

    static final String LEASE_EXTERNAL_ID = "lease_external_id";
    static final String PROPERTY_EXTERNAL_ID = "property_external_id";
    static final String PROPERTY_NAME = "property_name";
    static final String PROPERTY_TYPE = "property_type";
    static final String ADDRESS_LINE1 = "address_line1";
    static final String CITY = "city";
    static final String STATE = "state";
    static final String POSTAL_CODE = "postal_code";
    static final String TOTAL_RENTABLE_SQFT = "total_rentable_sqft";
    static final String TENANT_EXTERNAL_ID = "tenant_external_id";
    static final String TENANT_NAME = "tenant_name";
    static final String TENANT_INDUSTRY = "tenant_industry";
    static final String LEASED_SQFT = "leased_sqft";
    static final String LEASE_START_DATE = "lease_start_date";
    static final String LEASE_END_DATE = "lease_end_date";
    static final String RENEWAL_NOTICE_DATE = "renewal_notice_date";
    static final String ANNUAL_BASE_RENT = "annual_base_rent";
    static final String LEASE_STATUS = "lease_status";
    static final String ASSIGNED_MANAGER = "assigned_manager";
    static final String LAST_CONTACT_DATE = "last_contact_date";

    static final List<String> REQUIRED_HEADERS = List.of(
            LEASE_EXTERNAL_ID, PROPERTY_EXTERNAL_ID, PROPERTY_NAME, PROPERTY_TYPE, ADDRESS_LINE1,
            CITY, STATE, POSTAL_CODE, TOTAL_RENTABLE_SQFT, TENANT_EXTERNAL_ID, TENANT_NAME,
            TENANT_INDUSTRY, LEASED_SQFT, LEASE_START_DATE, LEASE_END_DATE, RENEWAL_NOTICE_DATE,
            ANNUAL_BASE_RENT, LEASE_STATUS, ASSIGNED_MANAGER, LAST_CONTACT_DATE);

    private LeaseCsvColumns() {
    }
}
