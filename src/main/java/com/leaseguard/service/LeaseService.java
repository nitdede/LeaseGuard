package com.leaseguard.service;

import com.leaseguard.exception.LeaseNotFoundException;
import com.leaseguard.exception.LeaseVersionConflictException;
import com.leaseguard.model.Lease;
import com.leaseguard.model.LeaseActionType;
import com.leaseguard.model.LeaseStatus;
import com.leaseguard.repository.LeaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Application service for lease assignment and status/workflow updates.
 *
 * <p>Both operations take the {@code expectedVersion} the form was rendered with and compare it
 * against the currently persisted {@link Lease#getVersion()} before mutating, so a manager
 * editing a stale page gets an explicit {@link LeaseVersionConflictException} instead of
 * silently overwriting a concurrent change. Each update and its {@code LeaseAction} audit record
 * are written in the same transaction.
 */
@Service
public class LeaseService {

    private final LeaseRepository leaseRepository;
    private final ActionService actionService;

    public LeaseService(LeaseRepository leaseRepository, ActionService actionService) {
        this.leaseRepository = leaseRepository;
        this.actionService = actionService;
    }

    // Assign a manager to the specified lease, recording the action in the audit log. If the manager name is blank, the manager is unassigned.
    @Transactional
    public Lease assignManager(Long leaseId, long expectedVersion, String managerName, String actorName) {
        Lease lease = findWithVersionCheck(leaseId, expectedVersion);
        String normalizedManager = StringUtils.hasText(managerName) ? managerName.trim() : null;
        lease.assignManager(normalizedManager);
        String note = normalizedManager == null ? "Manager unassigned" : "Assigned to " + normalizedManager;
        actionService.record(lease, LeaseActionType.MANAGER_ASSIGNED, note, actorName);
        return lease;
    }

    // Change the status of the specified lease, recording the action in the audit log. The provided note is used if present; otherwise, a default note is generated.
    @Transactional
    public Lease changeStatus(Long leaseId, long expectedVersion, LeaseStatus newStatus, String note, String actorName) {
        Lease lease = findWithVersionCheck(leaseId, expectedVersion);
        lease.changeStatus(newStatus);
        String actionNote = StringUtils.hasText(note) ? note.trim() : "Status changed to " + newStatus;
        actionService.record(lease, LeaseActionType.STATUS_CHANGED, actionNote, actorName);
        return lease;
    }

    // Retrieve the lease by ID and check that its version matches the expected version, throwing an exception if not.  
    private Lease findWithVersionCheck(Long leaseId, long expectedVersion) {
        Lease lease = leaseRepository.findById(leaseId).orElseThrow(() -> new LeaseNotFoundException(leaseId));
        if (lease.getVersion() != expectedVersion) {
            throw new LeaseVersionConflictException(leaseId);
        }
        return lease;
    }
}
