package com.leaseguard.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leaseguard.service.ActionService;
import com.leaseguard.dto.LeaseFilters;
import com.leaseguard.service.LeaseListService;
import com.leaseguard.exception.LeaseNotFoundException;
import com.leaseguard.repository.LeaseRepository;
import com.leaseguard.dto.LeaseRiskView;
import com.leaseguard.service.LeaseService;
import com.leaseguard.model.LeaseStatus;
import com.leaseguard.exception.LeaseVersionConflictException;
import com.leaseguard.dto.RiskLevel;
import com.leaseguard.repository.PropertyRepository;
import com.leaseguard.dto.PagedResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(LeaseController.class)
class LeaseControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private LeaseListService leaseListService;

    @MockitoBean
    private LeaseService leaseService;

    @MockitoBean
    private ActionService actionService;

    @MockitoBean
    private PropertyRepository propertyRepository;

    @MockitoBean
    private LeaseRepository leaseRepository;

    @Test
    void listRendersLeasesWithFilterOptions() {
        when(leaseListService.list(any(LeaseFilters.class), anyInt(), anyInt()))
                .thenReturn(new PagedResult<>(List.of(sampleView()), 0, 20, 1, 1));
        when(propertyRepository.findAllByOrderByNameAsc()).thenReturn(List.of());
        when(propertyRepository.findDistinctCities()).thenReturn(List.of("Dallas"));
        when(leaseRepository.findDistinctAssignedManagers()).thenReturn(List.of("Priya Shah"));

        assertThat(mvc.get().uri("/leases").param("riskLevel", "HIGH"))
                .hasStatusOk()
                .hasViewName("lease/list")
                .model().containsKeys("result", "filters", "properties", "cities", "managers");
    }

    @Test
    void detailRendersLeaseAndHistory() {
        when(leaseListService.detail(1L)).thenReturn(sampleView());
        when(actionService.historyFor(1L)).thenReturn(List.of());

        assertThat(mvc.get().uri("/leases/1"))
                .hasStatusOk()
                .hasViewName("lease/detail")
                .model().containsKey("lease");
    }

    @Test
    void detailForUnknownLeaseReturns404() {
        when(leaseListService.detail(999L)).thenThrow(new LeaseNotFoundException(999L));

        assertThat(mvc.get().uri("/leases/999"))
                .hasStatus(org.springframework.http.HttpStatus.NOT_FOUND)
                .hasViewName("error");
    }

    @Test
    void assignManagerRedirectsToLeaseDetailOnSuccess() {
        assertThat(mvc.post().uri("/leases/1/assign")
                        .param("version", "0")
                        .param("manager", "Priya Shah")
                        .param("actorName", "QA"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/leases/1");

        verify(leaseService).assignManager(eq(1L), eq(0L), eq("Priya Shah"), eq("QA"));
    }

    @Test
    void assignManagerWithStaleVersionStillRedirectsButFlashesConflict() {
        doThrow(new LeaseVersionConflictException(1L))
                .when(leaseService).assignManager(anyLong(), anyLong(), any(), any());

        assertThat(mvc.post().uri("/leases/1/assign")
                        .param("version", "0")
                        .param("manager", "Someone")
                        .param("actorName", "QA"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/leases/1")
                .flash().containsKey("conflictError");
    }

    @Test
    void changeStatusRedirectsToLeaseDetailOnSuccess() {
        assertThat(mvc.post().uri("/leases/1/status")
                        .param("version", "0")
                        .param("status", "CONTACT_TENANT")
                        .param("note", "left voicemail")
                        .param("actorName", "QA"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/leases/1");

        verify(leaseService).changeStatus(eq(1L), eq(0L), eq(LeaseStatus.CONTACT_TENANT), eq("left voicemail"), eq("QA"));
    }

    private LeaseRiskView sampleView() {
        return new LeaseRiskView(1L, "LSE-1001", "Tower", "Dallas", "Acme Inc", LocalDate.of(2026, 10, 31),
                LocalDate.of(2026, 4, 30), BigDecimal.valueOf(1_512_000), LeaseStatus.NOT_STARTED, null, 0L,
                75, RiskLevel.HIGH, List.of());
    }
}
