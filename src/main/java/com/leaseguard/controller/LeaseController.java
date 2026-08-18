package com.leaseguard.controller;

import com.leaseguard.service.ActionService;
import com.leaseguard.dto.LeaseFilters;
import com.leaseguard.service.LeaseListService;
import com.leaseguard.repository.LeaseRepository;
import com.leaseguard.service.LeaseService;
import com.leaseguard.model.LeaseStatus;
import com.leaseguard.exception.LeaseVersionConflictException;
import com.leaseguard.dto.RiskLevel;
import com.leaseguard.repository.PropertyRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/leases")
public class LeaseController {

    private static final int PAGE_SIZE = 20;

    private final LeaseListService leaseListService;
    private final LeaseService leaseService;
    private final ActionService actionService;
    private final PropertyRepository propertyRepository;
    private final LeaseRepository leaseRepository;

    public LeaseController(LeaseListService leaseListService, LeaseService leaseService, ActionService actionService,
                            PropertyRepository propertyRepository, LeaseRepository leaseRepository) {
        this.leaseListService = leaseListService;
        this.leaseService = leaseService;
        this.actionService = actionService;
        this.propertyRepository = propertyRepository;
        this.leaseRepository = leaseRepository;
    }

    // Display a paginated list of leases based on the provided filters, including property, city, status, manager, expiration, and risk level.
    @GetMapping
    public String list(@RequestParam(required = false) Long propertyId,
                        @RequestParam(required = false) String city,
                        @RequestParam(required = false) LeaseStatus status,
                        @RequestParam(required = false) String manager,
                        @RequestParam(required = false) Integer expiringWithinDays,
                        @RequestParam(required = false) RiskLevel riskLevel,
                        @RequestParam(defaultValue = "0") int page,
                        Model model) {
        LeaseFilters filters = new LeaseFilters(propertyId, city, status, manager, expiringWithinDays, riskLevel);
        model.addAttribute("result", leaseListService.list(filters, page, PAGE_SIZE));
        model.addAttribute("filters", filters);
        model.addAttribute("properties", propertyRepository.findAllByOrderByNameAsc());
        model.addAttribute("cities", propertyRepository.findDistinctCities());
        model.addAttribute("managers", leaseRepository.findDistinctAssignedManagers());
        model.addAttribute("statuses", LeaseStatus.values());
        model.addAttribute("riskLevels", RiskLevel.values());
        return "lease/list";
    }

    // Displays the details of a specific lease, including its action history and the list of possible statuses for display in the UI.
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        // Load the lease details and its action history, along with the list of possible statuses for display in the UI.
        model.addAttribute("lease", leaseListService.detail(id));

        // Load the action history for the lease, ordered by most recent first, and add it to the model for display in the UI.
        model.addAttribute("history", actionService.historyFor(id));

        // Load the list of possible statuses for leases and add it to the model for display in the UI.
        model.addAttribute("statuses", LeaseStatus.values());
        return "lease/detail";
    }

    /**
     * Handles the assignment of a manager to a lease, checking for version conflicts and redirecting back to the lease detail page with appropriate success or error messages.
     * This will called when the user submits the manager assignment form for a specific lease.
     */
    @PostMapping("/{id}/assign")
    public String assign(@PathVariable Long id, @RequestParam long version, @RequestParam(required = false) String manager,
                          @RequestParam(defaultValue = "Demo User") String actorName, RedirectAttributes redirectAttributes) {
        try {
            leaseService.assignManager(id, version, manager, actorName);
            redirectAttributes.addFlashAttribute("success", "Manager assignment updated.");
        } catch (LeaseVersionConflictException e) {
            redirectAttributes.addFlashAttribute("conflictError", e.getMessage());
        } catch (ObjectOptimisticLockingFailureException e) {
            redirectAttributes.addFlashAttribute("conflictError",
                    "Someone else updated this lease at the same time. Please review the latest details and try again.");
        }
        return "redirect:/leases/" + id;
    }

    /**
     * Handles the change of status for a lease, checking for version conflicts and redirecting back to the lease detail page with appropriate success or error messages.
     * This will be called when the user submits the status change form for a specific lease.
     */
    @PostMapping("/{id}/status")
    public String changeStatus(@PathVariable Long id, @RequestParam long version, @RequestParam LeaseStatus status,
                                @RequestParam(required = false) String note,
                                @RequestParam(defaultValue = "Demo User") String actorName,
                                RedirectAttributes redirectAttributes) {
        try {
            leaseService.changeStatus(id, version, status, note, actorName);
            redirectAttributes.addFlashAttribute("success", "Lease status updated.");
        } catch (LeaseVersionConflictException e) {
            redirectAttributes.addFlashAttribute("conflictError", e.getMessage());
        } catch (ObjectOptimisticLockingFailureException e) {
            redirectAttributes.addFlashAttribute("conflictError",
                    "Someone else updated this lease at the same time. Please review the latest details and try again.");
        }
        return "redirect:/leases/" + id;
    }
}
