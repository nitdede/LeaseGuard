package com.leaseguard.model;

import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An audit record of one committed CSV import. Only successful imports are persisted here - a
 * failed batch rolls back all business data, so there is nothing durable worth recording beyond
 * the row-level errors already shown to the user in the preview response.
 */
@Entity
@Table(name = "import_batches")
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private String checksum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportBatchStatus status;

    @Column(name = "row_count", nullable = false)
    private Integer rowCount;

    @Column(name = "success_count", nullable = false)
    private Integer successCount;

    @Column(name = "error_count", nullable = false)
    private Integer errorCount;

    @CreationTimestamp
    @Column(name = "imported_at", nullable = false, updatable = false)
    private Instant importedAt;

    protected ImportBatch() {
        // JPA
    }

    public ImportBatch(String filename, String checksum, ImportBatchStatus status, Integer rowCount,
                        Integer successCount, Integer errorCount) {
        this.filename = filename;
        this.checksum = checksum;
        this.status = status;
        this.rowCount = rowCount;
        this.successCount = successCount;
        this.errorCount = errorCount;
    }

    public Long getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public String getChecksum() {
        return checksum;
    }

    public ImportBatchStatus getStatus() {
        return status;
    }

    public Integer getRowCount() {
        return rowCount;
    }

    public Integer getSuccessCount() {
        return successCount;
    }

    public Integer getErrorCount() {
        return errorCount;
    }

    public Instant getImportedAt() {
        return importedAt;
    }
}
