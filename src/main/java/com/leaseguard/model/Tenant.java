package com.leaseguard.model;

import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A tenant occupying space under one or more leases. {@code externalId} is the stable business
 * key used to match rows across CSV imports. Unlike {@link Property}, a differing tenant name on
 * reimport is treated as a non-blocking warning and the latest name wins - see
 * {@code com.leaseguard.service.ImportService} for the rationale.
 */
@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, unique = true)
    private String externalId;

    @Column(nullable = false)
    private String name;

    @Column
    private String industry;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Tenant() {
        // JPA
    }

    public Tenant(String externalId, String name, String industry) {
        this.externalId = externalId;
        this.name = name;
        this.industry = industry;
    }

    public Long getId() {
        return id;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getName() {
        return name;
    }

    public String getIndustry() {
        return industry;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void renameTo(String name, String industry) {
        this.name = name;
        this.industry = industry;
    }
}
