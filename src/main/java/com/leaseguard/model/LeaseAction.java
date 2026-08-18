package com.leaseguard.model;

import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

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

/**
 * An append-only audit record of something that happened to a {@link Lease}: a manager
 * assignment, a status change, or a free-text note. Rows are never updated or deleted, so the
 * ordered history on the lease detail screen is a reliable audit trail.
 */
@Entity
@Table(name = "lease_actions")
public class LeaseAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lease_id", nullable = false)
    private Lease lease;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private LeaseActionType actionType;

    @Column
    private String note;

    @Column(name = "actor_name", nullable = false)
    private String actorName;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected LeaseAction() {
        // JPA
    }

    public LeaseAction(Lease lease, LeaseActionType actionType, String note, String actorName) {
        this.lease = lease;
        this.actionType = actionType;
        this.note = note;
        this.actorName = actorName;
    }

    public Long getId() {
        return id;
    }

    public Lease getLease() {
        return lease;
    }

    public LeaseActionType getActionType() {
        return actionType;
    }

    public String getNote() {
        return note;
    }

    public String getActorName() {
        return actorName;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
