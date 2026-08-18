package com.leaseguard.repository;

import com.leaseguard.model.LeaseAction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaseActionRepository extends JpaRepository<LeaseAction, Long> {

    List<LeaseAction> findByLeaseIdOrderByOccurredAtDesc(Long leaseId);
}
