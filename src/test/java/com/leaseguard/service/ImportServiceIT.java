package com.leaseguard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.leaseguard.AbstractIntegrationTest;
import com.leaseguard.dto.ImportCommitResult;
import com.leaseguard.dto.ImportPreviewResult;
import com.leaseguard.model.Lease;
import com.leaseguard.repository.ImportBatchRepository;
import com.leaseguard.repository.LeaseRepository;
import com.leaseguard.repository.PropertyRepository;
import com.leaseguard.repository.TenantRepository;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Exercises the full CSV preview/commit path against real PostgreSQL: atomic import, idempotent
 * reimport, update-in-place, all-or-nothing rollback, and the two batch-level conflict rules.
 */
class ImportServiceIT extends AbstractIntegrationTest {

    @Autowired
    private ImportService importService;

    @Autowired
    private LeaseRepository leaseRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private ImportBatchRepository importBatchRepository;

    @Test
    void importingBundledDemoCsvPersistsNormalizedRecordsInOneBatch() throws Exception {
        String content = Files.readString(Path.of("data/demo/lease_portfolio.csv"));

        ImportPreviewResult preview = importService.previewUpload("lease_portfolio.csv", content);
        assertThat(preview.canCommit()).isTrue();
        assertThat(preview.newCount()).isEqualTo(30);

        ImportCommitResult commit = importService.commit(preview.filename(), preview.content());

        assertThat(commit.successCount()).isEqualTo(30);
        assertThat(leaseRepository.count()).isEqualTo(30);
        assertThat(propertyRepository.count()).isEqualTo(8);
        assertThat(tenantRepository.count()).isEqualTo(30);
        assertThat(importBatchRepository.count()).isEqualTo(1);
    }

    @Test
    void reimportingTheSameFileCreatesNoDuplicates() throws Exception {
        String content = Files.readString(Path.of("data/demo/lease_portfolio.csv"));
        ImportPreviewResult firstPreview = importService.previewUpload("f.csv", content);
        importService.commit(firstPreview.filename(), firstPreview.content());

        ImportPreviewResult secondPreview = importService.previewUpload("f.csv", content);
        assertThat(secondPreview.newCount()).isZero();
        assertThat(secondPreview.changedCount()).isZero();
        assertThat(secondPreview.unchangedCount()).isEqualTo(30);

        importService.commit(secondPreview.filename(), secondPreview.content());

        assertThat(leaseRepository.count()).isEqualTo(30);
        assertThat(importBatchRepository.count()).isEqualTo(2); // two successful commits recorded, still 30 leases
    }

    @Test
    void reimportingWithAChangedFieldUpdatesTheExistingLeaseInPlace() {
        String original = csv(row(Map.of()));
        ImportPreviewResult firstPreview = importService.previewUpload("f.csv", original);
        importService.commit(firstPreview.filename(), firstPreview.content());
        Long originalId = leaseRepository.findByExternalId("LSE-1001").orElseThrow().getId();

        String changed = csv(row(Map.of("annual_base_rent", "1600000")));
        ImportPreviewResult preview = importService.previewUpload("f.csv", changed);
        assertThat(preview.changedCount()).isEqualTo(1);

        importService.commit(preview.filename(), preview.content());

        assertThat(leaseRepository.count()).isEqualTo(1);
        Lease updated = leaseRepository.findByExternalId("LSE-1001").orElseThrow();
        assertThat(updated.getId()).isEqualTo(originalId); // same row, not a duplicate insert
        assertThat(updated.getAnnualBaseRent()).isEqualByComparingTo(BigDecimal.valueOf(1_600_000));
    }

    @Test
    void invalidBatchPersistsNothingAtAll() {
        String invalid = csv(row(Map.of("lease_end_date", "2020-01-01"))); // end before start

        ImportPreviewResult preview = importService.previewUpload("bad.csv", invalid);

        assertThat(preview.canCommit()).isFalse();
        assertThat(preview.errorCount()).isEqualTo(1);
        assertThat(leaseRepository.count()).isZero();
        assertThat(propertyRepository.count()).isZero();
        assertThat(tenantRepository.count()).isZero();
    }

    @Test
    void duplicateLeaseExternalIdWithinFileBlocksTheWholeBatch() {
        String twoRowsSameId = csv(List.of(row(Map.of()), row(Map.of("tenant_external_id", "TEN-002"))));

        ImportPreviewResult preview = importService.previewUpload("dup.csv", twoRowsSameId);

        assertThat(preview.canCommit()).isFalse();
        assertThat(preview.errorCount()).isEqualTo(2);
        assertThat(preview.rows()).allSatisfy(r -> assertThat(r.issues())
                .anyMatch(i -> i.message().contains("Duplicate lease_external_id")));
    }

    @Test
    void conflictingPropertyAttributesForSameExternalIdBlocksTheBatch() {
        String row1 = row(Map.of("lease_external_id", "LSE-A", "property_name", "Tower One"));
        String row2 = row(Map.of("lease_external_id", "LSE-B", "tenant_external_id", "TEN-002",
                "property_name", "Tower Two")); // same property_external_id, different name

        ImportPreviewResult preview = importService.previewUpload("conflict.csv", csv(List.of(row1, row2)));

        assertThat(preview.canCommit()).isFalse();
        // The first occurrence establishes the reference attributes; only the differing
        // second row is flagged.
        assertThat(preview.errorCount()).isEqualTo(1);
        assertThat(preview.rows().get(1).issues())
                .anyMatch(issue -> issue.fieldName().equals("property_external_id"));
    }

    private String row(Map<String, String> overrides) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("lease_external_id", "LSE-1001");
        fields.put("property_external_id", "PROP-1");
        fields.put("property_name", "Test Tower");
        fields.put("property_type", "OFFICE");
        fields.put("address_line1", "1 Main St");
        fields.put("city", "Dallas");
        fields.put("state", "TX");
        fields.put("postal_code", "75001");
        fields.put("total_rentable_sqft", "100000");
        fields.put("tenant_external_id", "TEN-001");
        fields.put("tenant_name", "Acme Inc");
        fields.put("tenant_industry", "Technology");
        fields.put("leased_sqft", "5000");
        fields.put("lease_start_date", "2022-01-01");
        fields.put("lease_end_date", "2028-01-01");
        fields.put("renewal_notice_date", "2027-07-01");
        fields.put("annual_base_rent", "500000");
        fields.put("lease_status", "MONITOR");
        fields.put("assigned_manager", "Priya Shah");
        fields.put("last_contact_date", "2026-06-01");
        fields.putAll(overrides);
        return String.join(",", fields.values());
    }

    private String csv(String singleRow) {
        return csv(List.of(singleRow));
    }

    private String csv(List<String> rows) {
        String header = "lease_external_id,property_external_id,property_name,property_type,address_line1,city,"
                + "state,postal_code,total_rentable_sqft,tenant_external_id,tenant_name,tenant_industry,leased_sqft,"
                + "lease_start_date,lease_end_date,renewal_notice_date,annual_base_rent,lease_status,"
                + "assigned_manager,last_contact_date";
        return header + "\n" + rows.stream().collect(Collectors.joining("\n"));
    }
}
