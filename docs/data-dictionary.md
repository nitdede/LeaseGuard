# Demo CSV Data Dictionary

Primary file: `data/demo/lease_portfolio.csv`

All records are synthetic. When `demo.as-of-date=2026-08-17` is used, the dataset contains expired, urgent, medium-term, and low-risk scenarios.

| Column | Required | Type | Rule |
|---|---|---|---|
| lease_external_id | Yes | String | Unique, stable business key |
| property_external_id | Yes | String | Property attributes must be consistent for the same ID |
| property_name | Yes | String | 1–150 characters |
| property_type | Yes | Enum | OFFICE, RETAIL, INDUSTRIAL, MIXED_USE |
| address_line1 | Yes | String | Synthetic address |
| city | Yes | String | Non-blank |
| state | Yes | String | Two-letter US state code |
| postal_code | Yes | String | Keep as a string so leading zeroes are preserved |
| total_rentable_sqft | Yes | Integer | Positive |
| tenant_external_id | Yes | String | Stable tenant key |
| tenant_name | Yes | String | 1–150 characters |
| tenant_industry | No | String | Optional classification |
| leased_sqft | Yes | Integer | Positive and no greater than the property's total area |
| lease_start_date | Yes | ISO date | `yyyy-MM-dd` |
| lease_end_date | Yes | ISO date | Must be later than the start date |
| renewal_notice_date | No | ISO date | Must not be later than the end date; blank means no contractual option was recorded |
| annual_base_rent | Yes | Decimal | USD, non-negative, use `BigDecimal` |
| lease_status | Yes | Enum | NOT_STARTED, CONTACT_TENANT, RENEWAL_DISCUSSION, RENEWAL_IN_PROGRESS, RENEWED, TENANT_EXITING, MONITOR |
| assigned_manager | No | String | Blank means unassigned |
| last_contact_date | No | ISO date | Must not be in the future relative to the configured as-of date |

## Import invariants

- A duplicate `lease_external_id` within one file is an error.
- Importing an existing `lease_external_id` must not create a duplicate. The behavior may be upsert or reject, but it must be explicit and tested. MVP recommendation: preview changes, then perform an idempotent upsert.
- Conflicting property name, address, type, or size for the same `property_external_id` is a batch error.
- Conflicting tenant names for the same `tenant_external_id` must follow a documented warning-or-error policy.
- Do not persist records until file-level validation completes successfully.
- Normalize empty optional strings to `null`.

