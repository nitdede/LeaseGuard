package com.leaseguard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.leaseguard.AbstractIntegrationTest;
import com.leaseguard.exception.LeaseNotFoundException;
import com.leaseguard.exception.LeaseVersionConflictException;
import com.leaseguard.model.Lease;
import com.leaseguard.model.LeaseAction;
import com.leaseguard.model.LeaseActionType;
import com.leaseguard.model.LeaseStatus;
import com.leaseguard.model.Property;
import com.leaseguard.model.PropertyType;
import com.leaseguard.model.Tenant;
import com.leaseguard.repository.LeaseRepository;
import com.leaseguard.repository.PropertyRepository;
import com.leaseguard.repository.TenantRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class LeaseServiceIT extends AbstractIntegrationTest {

    @Autowired
    private LeaseService leaseService;

    @Autowired
    private LeaseRepository leaseRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private ActionService actionService;

    private Long leaseId;

    @BeforeEach
    void seedLease() {
        Property property = propertyRepository.save(new Property("PROP-IT", "Tower", PropertyType.OFFICE,
                "1 Main St", "Dallas", "TX", "75001", 100_000));
        Tenant tenant = tenantRepository.save(new Tenant("TEN-IT", "Acme Inc", "Technology"));
        Lease lease = leaseRepository.save(new Lease("LSE-IT", property, tenant, 5000, LocalDate.of(2022, 1, 1),
                LocalDate.of(2028, 1, 1), LocalDate.of(2027, 7, 1), BigDecimal.valueOf(500_000),
                LeaseStatus.MONITOR, null, null));
        leaseId = lease.getId();
    }

    // Every test method here runs inside one Spring-managed test transaction (rolled back at the
    // end), so unlike a real HTTP request - which is its own transaction that commits and flushes
    // on completion - Hibernate will not visibly bump @Version between two sequential service
    // calls unless the persistence context is explicitly flushed in between. The behavior under
    // test (LeaseService's manual version check) is transaction-boundary-independent, so an
    // explicit flush() after each mutation makes these tests exercise it faithfully.

    @Test
    void assignManagerSucceedsWithCurrentVersionAndRecordsAction() {
        Lease updated = leaseService.assignManager(leaseId, 0L, "Priya Shah", "QA Tester");
        leaseRepository.flush();

        assertThat(updated.getAssignedManager()).isEqualTo("Priya Shah");
        assertThat(updated.getVersion()).isEqualTo(1L);

        List<LeaseAction> history = actionService.historyFor(leaseId);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getActionType()).isEqualTo(LeaseActionType.MANAGER_ASSIGNED);
        assertThat(history.get(0).getActorName()).isEqualTo("QA Tester");
    }

    @Test
    void assignManagerWithStaleVersionThrowsConflictAndDoesNotApplyTheChange() {
        leaseService.assignManager(leaseId, 0L, "First Manager", "QA");
        leaseRepository.flush();

        assertThatThrownBy(() -> leaseService.assignManager(leaseId, 0L, "Second Manager", "QA"))
                .isInstanceOf(LeaseVersionConflictException.class);

        Lease current = leaseRepository.findById(leaseId).orElseThrow();
        assertThat(current.getAssignedManager()).isEqualTo("First Manager");
        assertThat(current.getVersion()).isEqualTo(1L);
    }

    @Test
    void changeStatusWithStaleVersionThrowsConflict() {
        leaseService.changeStatus(leaseId, 0L, LeaseStatus.CONTACT_TENANT, "first contact", "QA");
        leaseRepository.flush();

        assertThatThrownBy(() -> leaseService.changeStatus(leaseId, 0L, LeaseStatus.RENEWAL_DISCUSSION, "stale", "QA"))
                .isInstanceOf(LeaseVersionConflictException.class);

        Lease current = leaseRepository.findById(leaseId).orElseThrow();
        assertThat(current.getStatus()).isEqualTo(LeaseStatus.CONTACT_TENANT);
    }

    @Test
    void actionHistoryIsOrderedNewestFirst() throws InterruptedException {
        leaseService.assignManager(leaseId, 0L, "Priya Shah", "QA");
        leaseRepository.flush();
        Thread.sleep(5);
        leaseService.changeStatus(leaseId, 1L, LeaseStatus.CONTACT_TENANT, "note", "QA");

        List<LeaseAction> history = actionService.historyFor(leaseId);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getActionType()).isEqualTo(LeaseActionType.STATUS_CHANGED);
        assertThat(history.get(1).getActionType()).isEqualTo(LeaseActionType.MANAGER_ASSIGNED);
        assertThat(history.get(0).getOccurredAt()).isAfterOrEqualTo(history.get(1).getOccurredAt());
    }

    @Test
    void assigningUnknownLeaseThrowsNotFound() {
        assertThatThrownBy(() -> leaseService.assignManager(999_999L, 0L, "Someone", "QA"))
                .isInstanceOf(LeaseNotFoundException.class);
    }
}
