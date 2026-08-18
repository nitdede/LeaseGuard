package com.leaseguard.service;

import com.leaseguard.model.Lease;
import com.leaseguard.model.LeaseAction;
import com.leaseguard.model.LeaseActionType;
import com.leaseguard.repository.LeaseActionRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appends {@link LeaseAction} audit records. Rows are never updated or deleted - callers that
 * need to change a lease's own state (manager, status) do so on the {@link Lease} entity itself
 * and record the corresponding action in the same transaction.
 */
@Service
public class ActionService {

    private final LeaseActionRepository leaseActionRepository;

    public ActionService(LeaseActionRepository leaseActionRepository) {
        this.leaseActionRepository = leaseActionRepository;
    }

    // Save a new lease action in the audit log. This does not modify the lease itself.
    @Transactional
    public void record(Lease lease, LeaseActionType actionType, String note, String actorName) {
        leaseActionRepository.save(new LeaseAction(lease, actionType, note, actorName));
    }

    // Retrieve the audit history for a specific lease, ordered by the most recent actions first.
    @Transactional(readOnly = true)
    public List<LeaseAction> historyFor(Long leaseId) {
        return leaseActionRepository.findByLeaseIdOrderByOccurredAtDesc(leaseId);
    }
}
