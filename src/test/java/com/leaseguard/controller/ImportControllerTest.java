package com.leaseguard.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.leaseguard.dto.ImportCommitResult;
import com.leaseguard.dto.ImportPreviewResult;
import com.leaseguard.service.ImportService;
import com.leaseguard.exception.ImportValidationException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ImportController.class)
class ImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImportService importService;

    @Test
    void formRendersUploadPage() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/import"))
                .andExpect(status().isOk())
                .andExpect(view().name("import/form"));
    }

    @Test
    void previewWithEmptyFileShowsUploadErrorOnTheForm() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);

        mockMvc.perform(multipart("/import/preview").file(emptyFile))
                .andExpect(status().isOk())
                .andExpect(view().name("import/form"))
                .andExpect(model().attributeExists("uploadError"));
    }

    @Test
    void previewWithContentRendersPreviewPage() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "leases.csv", "text/csv", "content".getBytes());
        when(importService.previewUpload("leases.csv", "content"))
                .thenReturn(new ImportPreviewResult("content", "leases.csv", null, 1, 1, 0, 0, 0, 0, List.of()));

        mockMvc.perform(multipart("/import/preview").file(file))
                .andExpect(status().isOk())
                .andExpect(view().name("import/preview"))
                .andExpect(model().attributeExists("preview"));
    }

    @Test
    void previewDemoDataRendersPreviewPage() throws Exception {
        when(importService.previewBundledDemoData())
                .thenReturn(new ImportPreviewResult("demo-content", "demo.csv", null, 30, 30, 0, 0, 0, 0, List.of()));

        mockMvc.perform(post("/import/preview-demo"))
                .andExpect(status().isOk())
                .andExpect(view().name("import/preview"))
                .andExpect(model().attributeExists("preview"));
    }

    @Test
    void commitSuccessRedirectsToDashboardWithFlash() throws Exception {
        when(importService.commit("leases.csv", "content"))
                .thenReturn(new ImportCommitResult(1L, "leases.csv", 30, Instant.now()));

        mockMvc.perform(post("/import/commit").param("filename", "leases.csv").param("content", "content"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andExpect(flash().attributeExists("importSuccess"));
    }

    @Test
    void commitWithRevalidationFailureRendersPreviewAgain() throws Exception {
        ImportPreviewResult refreshed = new ImportPreviewResult(null, "leases.csv", null, 1, 0, 0, 0, 0, 1, List.of());
        when(importService.commit("leases.csv", "content")).thenThrow(new ImportValidationException(refreshed));

        mockMvc.perform(post("/import/commit").param("filename", "leases.csv").param("content", "content"))
                .andExpect(status().isOk())
                .andExpect(view().name("import/preview"))
                .andExpect(model().attributeExists("preview", "uploadError"));
    }
}
