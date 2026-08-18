package com.leaseguard.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.leaseguard.service.DashboardService;
import com.leaseguard.dto.DashboardView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private DashboardService dashboardService;

    @Test
    void rootRedirectsToDashboard() {
        assertThat(mvc.get().uri("/"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/dashboard");
    }

    @Test
    void dashboardRendersKpisFromService() {
        when(dashboardService.summarize()).thenReturn(new DashboardView(
                LocalDate.of(2026, 8, 17), 30, BigDecimal.valueOf(48_320_500), 7, 13, 20,
                BigDecimal.valueOf(29_776_500), 16, 8, 5, 3, 2, 20, List.of()));

        assertThat(mvc.get().uri("/dashboard"))
                .hasStatusOk()
                .hasViewName("dashboard")
                .model().containsKey("dashboard");
    }
}
