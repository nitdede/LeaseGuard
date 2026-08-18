package com.leaseguard.controller;

import com.leaseguard.service.DashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    // Redirect the root URL to the dashboard view.
    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }

    // Display the dashboard view with summarized lease information.
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("dashboard", dashboardService.summarize());
        return "dashboard";
    }
}
