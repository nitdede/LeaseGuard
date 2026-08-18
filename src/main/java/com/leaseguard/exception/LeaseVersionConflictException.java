package com.leaseguard.exception;

/**
 * Thrown when a lease-update form was submitted against a version older than what is currently
 * persisted, meaning another user changed the lease first. The web layer maps this to a
 * user-facing conflict message rather than silently overwriting the newer change.
 */
public class LeaseVersionConflictException extends RuntimeException {

    public LeaseVersionConflictException(Long leaseId) {
        super("Lease " + leaseId + " was changed by someone else. Please reload and try again.");
    }
}
