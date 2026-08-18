package com.leaseguard.exception;

import com.leaseguard.dto.ImportPreviewResult;

/**
 * Thrown if a commit's server-side revalidation (against the original uploaded bytes) finds
 * errors that were not present, or no longer hold, at preview time - e.g. a lease imported by
 * someone else in between. Carries the fresh {@link ImportPreviewResult} so the UI can show
 * exactly what changed.
 */
public class ImportValidationException extends RuntimeException {

    private final transient ImportPreviewResult result;

    public ImportValidationException(ImportPreviewResult result) {
        super("Import batch failed revalidation and was not committed.");
        this.result = result;
    }

    public ImportPreviewResult result() {
        return result;
    }
}
