package com.leaseguard.model;

import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A physical property in the portfolio. {@code externalId} is the stable business key used to
 * match rows across CSV imports; property attributes must stay consistent for a given external
 * ID (a conflicting reimport is rejected - see {@code com.leaseguard.service.ImportService}).
 */
@Entity
@Table(name = "properties")
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, unique = true)
    private String externalId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", nullable = false)
    private PropertyType propertyType;

    @Column(name = "address_line1", nullable = false)
    private String addressLine1;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String state;

    @Column(name = "postal_code", nullable = false)
    private String postalCode;

    @Column(name = "total_rentable_sqft", nullable = false)
    private Integer totalRentableSqFt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Property() {
        // JPA
    }

    public Property(String externalId, String name, PropertyType propertyType, String addressLine1,
                     String city, String state, String postalCode, Integer totalRentableSqFt) {
        this.externalId = externalId;
        this.name = name;
        this.propertyType = propertyType;
        this.addressLine1 = addressLine1;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.totalRentableSqFt = totalRentableSqFt;
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

    public PropertyType getPropertyType() {
        return propertyType;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public Integer getTotalRentableSqFt() {
        return totalRentableSqFt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** True if the given attributes differ from this property's current attributes. */
    public boolean conflictsWith(String name, PropertyType propertyType, String addressLine1,
                                  String city, String state, String postalCode, Integer totalRentableSqFt) {
        return !this.name.equals(name)
                || this.propertyType != propertyType
                || !this.addressLine1.equals(addressLine1)
                || !this.city.equals(city)
                || !this.state.equals(state)
                || !this.postalCode.equals(postalCode)
                || !this.totalRentableSqFt.equals(totalRentableSqFt);
    }
}
