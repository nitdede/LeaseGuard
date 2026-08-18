package com.leaseguard.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A single lease agreement between a {@link Property} and a {@link Tenant}. {@code externalId}
 * is the idempotency key for CSV import (see {@code com.leaseguard.service.ImportService}).
 * {@code version} backs optimistic locking so two managers editing the same lease concurrently
 * get a conflict instead of a silently lost update.
 */
@Entity
@Table(name = "leases")
public class Lease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, unique = true)
    private String externalId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "leased_sqft", nullable = false)
    private Integer leasedSqFt;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "renewal_notice_date")
    private LocalDate renewalNoticeDate;

    @Column(name = "annual_base_rent", nullable = false, precision = 14, scale = 2)
    private BigDecimal annualBaseRent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaseStatus status;

    @Column(name = "assigned_manager")
    private String assignedManager;

    @Column(name = "last_contact_date")
    private LocalDate lastContactDate;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Lease() {
        // JPA
    }

    public Lease(String externalId, Property property, Tenant tenant, Integer leasedSqFt,
                 LocalDate startDate, LocalDate endDate, LocalDate renewalNoticeDate,
                 BigDecimal annualBaseRent, LeaseStatus status, String assignedManager,
                 LocalDate lastContactDate) {
        this.externalId = externalId;
        this.property = property;
        this.tenant = tenant;
        this.leasedSqFt = leasedSqFt;
        this.startDate = startDate;
        this.endDate = endDate;
        this.renewalNoticeDate = renewalNoticeDate;
        this.annualBaseRent = annualBaseRent;
        this.status = status;
        this.assignedManager = assignedManager;
        this.lastContactDate = lastContactDate;
    }

    /**
     * Overwrites all mutable fields, including the property/tenant association, from a
     * reimported CSV row with the same external ID.
     */
    public void applyImportedValues(Property property, Tenant tenant, Integer leasedSqFt, LocalDate startDate,
                                     LocalDate endDate, LocalDate renewalNoticeDate, BigDecimal annualBaseRent,
                                     LeaseStatus status, String assignedManager, LocalDate lastContactDate) {
        this.property = property;
        this.tenant = tenant;
        this.leasedSqFt = leasedSqFt;
        this.startDate = startDate;
        this.endDate = endDate;
        this.renewalNoticeDate = renewalNoticeDate;
        this.annualBaseRent = annualBaseRent;
        this.status = status;
        this.assignedManager = assignedManager;
        this.lastContactDate = lastContactDate;
    }

    public void assignManager(String assignedManager) {
        this.assignedManager = assignedManager;
    }

    public void changeStatus(LeaseStatus status) {
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getExternalId() {
        return externalId;
    }

    public Property getProperty() {
        return property;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public Integer getLeasedSqFt() {
        return leasedSqFt;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public LocalDate getRenewalNoticeDate() {
        return renewalNoticeDate;
    }

    public BigDecimal getAnnualBaseRent() {
        return annualBaseRent;
    }

    public LeaseStatus getStatus() {
        return status;
    }

    public String getAssignedManager() {
        return assignedManager;
    }

    public LocalDate getLastContactDate() {
        return lastContactDate;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** True if any field that matters for import change-detection differs from the given CSV values. */
    public boolean differsFrom(String propertyExternalId, String tenantExternalId, Integer leasedSqFt,
                                LocalDate startDate, LocalDate endDate, LocalDate renewalNoticeDate,
                                BigDecimal annualBaseRent, LeaseStatus status, String assignedManager,
                                LocalDate lastContactDate) {
        return !this.property.getExternalId().equals(propertyExternalId)
                || !this.tenant.getExternalId().equals(tenantExternalId)
                || !this.leasedSqFt.equals(leasedSqFt)
                || !this.startDate.equals(startDate)
                || !this.endDate.equals(endDate)
                || !java.util.Objects.equals(this.renewalNoticeDate, renewalNoticeDate)
                || this.annualBaseRent.compareTo(annualBaseRent) != 0
                || this.status != status
                || !java.util.Objects.equals(this.assignedManager, assignedManager)
                || !java.util.Objects.equals(this.lastContactDate, lastContactDate);
    }
}
