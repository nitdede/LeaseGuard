-- Core LeaseGuard schema: properties, tenants, leases, lease actions, and import batches.
-- Flyway applies this once per database; Hibernate is configured as validate-only and never
-- creates or alters these tables (see application.yml: spring.jpa.hibernate.ddl-auto=validate).

CREATE TABLE properties (
    id                   BIGSERIAL PRIMARY KEY,
    external_id          VARCHAR(64)     NOT NULL,
    name                 VARCHAR(150)    NOT NULL,
    property_type        VARCHAR(20)     NOT NULL,
    address_line1        VARCHAR(200)    NOT NULL,
    city                 VARCHAR(100)    NOT NULL,
    state                VARCHAR(2)      NOT NULL,
    postal_code          VARCHAR(10)     NOT NULL,
    total_rentable_sqft  INTEGER         NOT NULL,
    created_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_properties_external_id UNIQUE (external_id),
    CONSTRAINT chk_properties_type CHECK (property_type IN ('OFFICE', 'RETAIL', 'INDUSTRIAL', 'MIXED_USE')),
    CONSTRAINT chk_properties_sqft_positive CHECK (total_rentable_sqft > 0)
);

CREATE TABLE tenants (
    id           BIGSERIAL PRIMARY KEY,
    external_id  VARCHAR(64)   NOT NULL,
    name         VARCHAR(150)  NOT NULL,
    industry     VARCHAR(100),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_tenants_external_id UNIQUE (external_id)
);

CREATE TABLE leases (
    id                   BIGSERIAL PRIMARY KEY,
    external_id          VARCHAR(64)     NOT NULL,
    property_id          BIGINT          NOT NULL REFERENCES properties (id),
    tenant_id            BIGINT          NOT NULL REFERENCES tenants (id),
    leased_sqft          INTEGER         NOT NULL,
    start_date           DATE            NOT NULL,
    end_date             DATE            NOT NULL,
    renewal_notice_date  DATE,
    annual_base_rent     NUMERIC(14, 2)  NOT NULL,
    status               VARCHAR(30)     NOT NULL,
    assigned_manager     VARCHAR(150),
    last_contact_date    DATE,
    version              BIGINT          NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_leases_external_id UNIQUE (external_id),
    CONSTRAINT chk_leases_status CHECK (status IN (
        'NOT_STARTED', 'CONTACT_TENANT', 'RENEWAL_DISCUSSION', 'RENEWAL_IN_PROGRESS',
        'RENEWED', 'TENANT_EXITING', 'MONITOR'
    )),
    CONSTRAINT chk_leases_sqft_positive CHECK (leased_sqft > 0),
    CONSTRAINT chk_leases_end_after_start CHECK (end_date > start_date),
    CONSTRAINT chk_leases_notice_before_end CHECK (renewal_notice_date IS NULL OR renewal_notice_date <= end_date),
    CONSTRAINT chk_leases_rent_non_negative CHECK (annual_base_rent >= 0)
);

CREATE INDEX idx_leases_property_id ON leases (property_id);
CREATE INDEX idx_leases_tenant_id ON leases (tenant_id);
CREATE INDEX idx_leases_status ON leases (status);
CREATE INDEX idx_leases_end_date ON leases (end_date);
CREATE INDEX idx_leases_assigned_manager ON leases (assigned_manager);

CREATE TABLE lease_actions (
    id           BIGSERIAL PRIMARY KEY,
    lease_id     BIGINT        NOT NULL REFERENCES leases (id),
    action_type  VARCHAR(30)   NOT NULL,
    note         VARCHAR(2000),
    actor_name   VARCHAR(150)  NOT NULL,
    occurred_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_lease_actions_type CHECK (action_type IN ('MANAGER_ASSIGNED', 'STATUS_CHANGED'))
);

-- Append-only audit trail: ordered by lease, newest first, on the lease detail screen.
CREATE INDEX idx_lease_actions_lease_id_occurred_at ON lease_actions (lease_id, occurred_at DESC);

CREATE TABLE import_batches (
    id             BIGSERIAL PRIMARY KEY,
    filename       VARCHAR(255)  NOT NULL,
    checksum       VARCHAR(64)   NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    row_count      INTEGER       NOT NULL,
    success_count  INTEGER       NOT NULL,
    error_count    INTEGER       NOT NULL,
    imported_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_import_batches_status CHECK (status = 'SUCCESS')
);
