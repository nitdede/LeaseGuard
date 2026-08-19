package com.leaseguard.service;

import com.leaseguard.dto.ImportCommitResult;
import com.leaseguard.dto.ImportIssue;
import com.leaseguard.dto.ImportPreviewResult;
import com.leaseguard.dto.IssueSeverity;
import com.leaseguard.dto.RowOutcome;
import com.leaseguard.dto.RowStatus;
import com.leaseguard.exception.ImportFileFormatException;
import com.leaseguard.exception.ImportValidationException;
import com.leaseguard.model.ImportBatch;
import com.leaseguard.model.ImportBatchStatus;
import com.leaseguard.model.Lease;
import com.leaseguard.model.Property;
import com.leaseguard.model.Tenant;
import com.leaseguard.repository.ImportBatchRepository;
import com.leaseguard.repository.LeaseRepository;
import com.leaseguard.repository.PropertyRepository;
import com.leaseguard.repository.TenantRepository;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates CSV preview and atomic idempotent import.
 *
 * <p><b>Idempotency:</b> {@code lease_external_id} is the business key. A row whose ID does not
 * yet exist is an insert (NEW); an existing ID with identical field values is a no-op
 * (UNCHANGED); an existing ID with different values is an update (CHANGED). Reimporting the
 * exact same file therefore produces zero duplicate rows.
 *
 * <p><b>Transaction boundary:</b> {@link #commit(String, String)} is all-or-nothing. Preview
 * never writes to the database. The preview page echoes the exact file content back to the
 * browser in a hidden field, and commit re-validates that content from scratch (not any
 * client-supplied row/error counts) before writing anything - if any row still has an error,
 * nothing is persisted at all. The application holds no server-side state between preview and
 * commit.
 */
@Service
public class ImportService {

    private final LeaseCsvReader csvReader;
    private final CsvRowValidator rowValidator;
    private final PropertyRepository propertyRepository;
    private final TenantRepository tenantRepository;
    private final LeaseRepository leaseRepository;
    private final ImportBatchRepository importBatchRepository;
    private final Clock clock;
    private final String bundledCsvPath;

    public ImportService(LeaseCsvReader csvReader, CsvRowValidator rowValidator,
                          PropertyRepository propertyRepository, TenantRepository tenantRepository,
                          LeaseRepository leaseRepository, ImportBatchRepository importBatchRepository,
                          Clock clock, com.leaseguard.config.ClockConfig.DemoProperties demoProperties) {
        this.csvReader = csvReader;
        this.rowValidator = rowValidator;
        this.propertyRepository = propertyRepository;
        this.tenantRepository = tenantRepository;
        this.leaseRepository = leaseRepository;
        this.importBatchRepository = importBatchRepository;
        this.clock = clock;
        this.bundledCsvPath = demoProperties.bundledCsvPath();
    }

    // Returns a preview of the given CSV content, which is validated but not yet persisted. This
    // runs read-only so classifyIdempotency() can safely read a matched lease's lazy property/tenant
    // association (open-in-view is disabled, so that association is not reachable once this method returns).
    @Transactional(readOnly = true)
    public ImportPreviewResult previewUpload(String filename, String content) {
        return computePreview(content, filename).result();
    }

    // Returns a preview of the bundled demo CSV file, which is read from the classpath. This is used
    @Transactional(readOnly = true)
    public ImportPreviewResult previewBundledDemoData() {
        try {
            String content = new FileSystemResource(bundledCsvPath).getContentAsString(StandardCharsets.UTF_8);
            return computePreview(content, "lease_portfolio.csv (bundled demo data)").result();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read bundled demo CSV at " + bundledCsvPath, e);
        }
    }

    // Commits the given CSV content to the database, returning a summary of what was imported.
    @Transactional
    public ImportCommitResult commit(String filename, String content) {
        PreviewComputation computation = computePreview(content, filename);
        if (!computation.result().canCommit()) {
            throw new ImportValidationException(computation.result());
        }

        for (ParsedLeaseRow row : computation.validRows()) {
            Property property = upsertProperty(row);
            Tenant tenant = upsertTenant(row);
            upsertLease(row, property, tenant);
        }

        ImportBatch batch = new ImportBatch(filename, checksum(content), ImportBatchStatus.SUCCESS,
                computation.result().totalRows(), computation.validRows().size(), 0);
        importBatchRepository.save(batch);

        return new ImportCommitResult(batch.getId(), batch.getFilename(), batch.getSuccessCount(), batch.getImportedAt());
    }

    // Computes the SHA-256 checksum of the given content and returns it as a hexadecimal string.
    private PreviewComputation computePreview(String content, String filename) {
        List<RawCsvRow> rawRows;
        try {
            rawRows = csvReader.read(new StringReader(content));
        } catch (ImportFileFormatException e) {
            return new PreviewComputation(ImportPreviewResult.fileError(filename, e.getMessage()), List.of());
        }

        LocalDate asOf = LocalDate.now(clock);
        List<RowValidationResult> validations = rawRows.stream().map(row -> rowValidator.validate(row, asOf)).toList();

        Map<Integer, List<ImportIssue>> extraIssues = new HashMap<>();
        detectDuplicateLeaseIds(rawRows, extraIssues);
        detectPropertyConflicts(validations, extraIssues);
        detectTenantConflicts(validations, extraIssues);

        List<RowOutcome> outcomes = new ArrayList<>();
        List<ParsedLeaseRow> validRows = new ArrayList<>();
        int newCount = 0;
        int changedCount = 0;
        int unchangedCount = 0;
        int warningCount = 0;
        int errorCount = 0;

        for (RawCsvRow rawRow : rawRows) {
            RowValidationResult validation = validations.stream()
                    .filter(v -> v.rowNumber() == rawRow.rowNumber())
                    .findFirst().orElseThrow();
            List<ImportIssue> issues = new ArrayList<>(validation.errors());
            issues.addAll(extraIssues.getOrDefault(validation.rowNumber(), List.of()));

            boolean hasError = issues.stream().anyMatch(i -> i.severity() == IssueSeverity.ERROR);
            boolean hasWarning = issues.stream().anyMatch(i -> i.severity() == IssueSeverity.WARNING);
            String leaseExternalId = validation.parsedRow() != null
                    ? validation.parsedRow().leaseExternalId()
                    : rawRow.get(LeaseCsvColumns.LEASE_EXTERNAL_ID);

            RowStatus status;
            if (hasError) {
                status = RowStatus.ERROR;
                errorCount++;
            } else if (hasWarning) {
                status = RowStatus.WARNING;
                warningCount++;
                validRows.add(validation.parsedRow());
            } else {
                status = classifyIdempotency(validation.parsedRow());
                validRows.add(validation.parsedRow());
                switch (status) {
                    case NEW -> newCount++;
                    case CHANGED -> changedCount++;
                    case UNCHANGED -> unchangedCount++;
                    default -> throw new IllegalStateException("Unexpected idempotency status: " + status);
                }
            }
            outcomes.add(new RowOutcome(validation.rowNumber(), leaseExternalId, status, issues));
        }

        boolean canCommit = errorCount == 0;
        ImportPreviewResult result = new ImportPreviewResult(canCommit ? content : null, filename, null,
                rawRows.size(), newCount, changedCount, unchangedCount, warningCount, errorCount, outcomes);
        return new PreviewComputation(result, validRows);
    }

    /**
     * Check if the lease_external_id already exists in the database. 
     * If it does, compare the values of the existing lease with the values of the new row. 
     * If any of the values differ, return RowStatus.CHANGED. 
     * If all values are the same, return RowStatus.UNCHANGED. 
     * If the lease_external_id does not exist in the database, return RowStatus.NEW.
     */
    private RowStatus classifyIdempotency(ParsedLeaseRow row) {
        return leaseRepository.findByExternalId(row.leaseExternalId())
                .map(existing -> existing.differsFrom(row.propertyExternalId(), row.tenantExternalId(),
                        row.leasedSqFt(), row.leaseStartDate(), row.leaseEndDate(), row.renewalNoticeDate(),
                        row.annualBaseRent(), row.leaseStatus(), row.assignedManager(), row.lastContactDate())
                        ? RowStatus.CHANGED
                        : RowStatus.UNCHANGED)
                .orElse(RowStatus.NEW);
    }

   // Detects duplicate lease_external_id values within the same CSV file and adds an error to the extraIssues map for each duplicate row.
    private void detectDuplicateLeaseIds(List<RawCsvRow> rawRows, Map<Integer, List<ImportIssue>> extraIssues) {
        Map<String, List<Integer>> rowNumbersByLeaseId = new HashMap<>();
        for (RawCsvRow row : rawRows) {
            String leaseExternalId = row.get(LeaseCsvColumns.LEASE_EXTERNAL_ID);
            if (leaseExternalId != null) {
                // Group row numbers by leaseExternalId to detect duplicates within the same CSV file.
                rowNumbersByLeaseId.computeIfAbsent(leaseExternalId, id -> new ArrayList<>()).add(row.rowNumber());
            }
        }
        // Report duplicates within the same CSV file.
        for (Map.Entry<String, List<Integer>> entry : rowNumbersByLeaseId.entrySet()) {
            if (entry.getValue().size() > 1) {
                for (Integer rowNumber : entry.getValue()) {
                    addIssue(extraIssues, ImportIssue.error(rowNumber, LeaseCsvColumns.LEASE_EXTERNAL_ID,
                            entry.getKey(), "Duplicate lease_external_id within this file."));
                }
            }
        }
    }

    /**
     * This function checks for property conflicts by comparing the attributes of properties with the same external ID. 
     * If there are any discrepancies between the attributes of the properties, it adds an error to the extraIssues map for each conflicting row.
     * Example : if the same property is described two different ways.
     */
    private void detectPropertyConflicts(List<RowValidationResult> validations, Map<Integer, List<ImportIssue>> extraIssues) {
        Map<String, List<RowValidationResult>> byPropertyId = new HashMap<>();
        for (RowValidationResult v : validations) {
            if (v.parsedRow() != null) {
                byPropertyId.computeIfAbsent(v.parsedRow().propertyExternalId(), id -> new ArrayList<>()).add(v);
            }
        }

        // Check each property for conflicts with existing records or other rows in the CSV.
        for (Map.Entry<String, List<RowValidationResult>> entry : byPropertyId.entrySet()) {
            Optional<Property> existing = propertyRepository.findByExternalId(entry.getKey());
            ParsedLeaseRow reference = existing.isEmpty() ? entry.getValue().get(0).parsedRow() : null;
            for (RowValidationResult v : entry.getValue()) {
                ParsedLeaseRow row = v.parsedRow();
                boolean conflict = existing.isPresent()
                        ? existing.get().conflictsWith(row.propertyName(), row.propertyType(), row.addressLine1(),
                                row.city(), row.state(), row.postalCode(), row.totalRentableSqFt())
                        : !propertyAttributesEqual(row, reference);
                if (conflict) {
                    addIssue(extraIssues, ImportIssue.error(v.rowNumber(), LeaseCsvColumns.PROPERTY_EXTERNAL_ID,
                            entry.getKey(), "Property attributes for " + entry.getKey()
                                    + " conflict with another row or the existing record."));
                }
            }
        }
    }

    /**
     * Checks: does the same tenant (tenant_external_id) get called by two different names anywhere — either within this file, 
     * or compared to what's already saved in the database? If yes, 
     * it flags the differing rows with a warning (not an error — reimport still proceeds).
     */
    private void detectTenantConflicts(List<RowValidationResult> validations, Map<Integer, List<ImportIssue>> extraIssues) {
        Map<String, List<RowValidationResult>> byTenantId = new HashMap<>();
        for (RowValidationResult v : validations) {
            if (v.parsedRow() != null) {
                byTenantId.computeIfAbsent(v.parsedRow().tenantExternalId(), id -> new ArrayList<>()).add(v);
            }
        }
        for (Map.Entry<String, List<RowValidationResult>> entry : byTenantId.entrySet()) {
            Optional<Tenant> existing = tenantRepository.findByExternalId(entry.getKey());
            String referenceName = existing.map(Tenant::getName).orElse(entry.getValue().get(0).parsedRow().tenantName());
            Set<String> names = new HashSet<>();
            for (RowValidationResult v : entry.getValue()) {
                names.add(v.parsedRow().tenantName());
            }
            boolean nameVaries = names.size() > 1 || !names.contains(referenceName);
            if (nameVaries) {
                for (RowValidationResult v : entry.getValue()) {
                    if (!v.parsedRow().tenantName().equals(referenceName)) {
                        addIssue(extraIssues, ImportIssue.warning(v.rowNumber(), LeaseCsvColumns.TENANT_NAME,
                                v.parsedRow().tenantName(), "Tenant name differs from the existing/other rows for "
                                        + entry.getKey() + "; the latest name will be used."));
                    }
                }
            }
        }
    }

    // Compares the property attributes of two ParsedLeaseRow objects to determine if they are equal. Returns true if all attributes are equal, false otherwise.
    private boolean propertyAttributesEqual(ParsedLeaseRow a, ParsedLeaseRow b) {
        return a.propertyName().equals(b.propertyName())
                && a.propertyType() == b.propertyType()
                && a.addressLine1().equals(b.addressLine1())
                && a.city().equals(b.city())
                && a.state().equals(b.state())
                && a.postalCode().equals(b.postalCode())
                && a.totalRentableSqFt().equals(b.totalRentableSqFt());
    }

    // Adds an ImportIssue to the extraIssues map for the given row number. If there are already issues for that row, the new issue is added to the existing list; otherwise, a new list is created.
    private void addIssue(Map<Integer, List<ImportIssue>> extraIssues, ImportIssue issue) {
        extraIssues.computeIfAbsent(issue.rowNumber(), r -> new ArrayList<>()).add(issue);
    }

    
    // Find-or-create only: property conflicts are rejected as errors before commit, so an existing property always matches and needs no update.
    private Property upsertProperty(ParsedLeaseRow row) {
        return propertyRepository.findByExternalId(row.propertyExternalId())
                .orElseGet(() -> propertyRepository.save(new Property(row.propertyExternalId(), row.propertyName(),
                        row.propertyType(), row.addressLine1(), row.city(), row.state(), row.postalCode(),
                        row.totalRentableSqFt())));
    }

    // Upserts a Tenant based on the given ParsedLeaseRow. If a tenant with the same external ID already exists, it is updated with the new name and industry; otherwise, a new Tenant is created and saved to the repository.
    private Tenant upsertTenant(ParsedLeaseRow row) {
        return tenantRepository.findByExternalId(row.tenantExternalId())
                .map(existing -> {
                    existing.renameTo(row.tenantName(), row.tenantIndustry());
                    return existing;
                })
                .orElseGet(() -> tenantRepository.save(new Tenant(row.tenantExternalId(), row.tenantName(), row.tenantIndustry())));
    }

    // Upserts a Lease based on the given ParsedLeaseRow, Property, and Tenant. 
    // If a lease with the same external ID already exists, it is updated with the new values using JPA Dirty Checking
    // otherwise, a new Lease is created and saved to the repository.
    private void upsertLease(ParsedLeaseRow row, Property property, Tenant tenant) {
        Optional<Lease> existing = leaseRepository.findByExternalId(row.leaseExternalId());
        if (existing.isPresent()) {
            Lease lease = existing.get();
            if (lease.differsFrom(row.propertyExternalId(), row.tenantExternalId(), row.leasedSqFt(),
                    row.leaseStartDate(), row.leaseEndDate(), row.renewalNoticeDate(), row.annualBaseRent(),
                    row.leaseStatus(), row.assignedManager(), row.lastContactDate())) {

                lease.applyImportedValues(property, tenant, row.leasedSqFt(), row.leaseStartDate(),
                        row.leaseEndDate(), row.renewalNoticeDate(), row.annualBaseRent(), row.leaseStatus(),
                        row.assignedManager(), row.lastContactDate());
            }
        } else {
            leaseRepository.save(new Lease(row.leaseExternalId(), property, tenant, row.leasedSqFt(),
                    row.leaseStartDate(), row.leaseEndDate(), row.renewalNoticeDate(), row.annualBaseRent(),
                    row.leaseStatus(), row.assignedManager(), row.lastContactDate()));
        }
    }

    // Computes the SHA-256 checksum of the given content and returns it as a hexadecimal string. This is used to ensure that the content has not been tampered with between preview and commit.
    private String checksum(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }

    // A simple record to hold the result of a preview computation, including the ImportPreviewResult and the list of valid ParsedLeaseRow objects.
    private record PreviewComputation(ImportPreviewResult result, List<ParsedLeaseRow> validRows) {
    }
}
