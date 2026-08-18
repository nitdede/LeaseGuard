package com.leaseguard.dto;

import java.util.List;

/**
 * The result of validating an uploaded (or bundled demo) CSV without persisting anything.
 * {@code content} is the exact file text that was validated - the preview page echoes it back to
 * the browser in a hidden field so {@code POST /import/commit} can re-validate it from scratch
 * rather than trusting client-supplied row/error counts. It is null whenever the batch cannot be
 * committed. The application keeps no server-side record of the upload between requests.
 */
public record ImportPreviewResult(
        String content,
        String filename,
        String fileLevelError,
        int totalRows,
        int newCount,
        int changedCount,
        int unchangedCount,
        int warningCount,
        int errorCount,
        List<RowOutcome> rows) {

    public static ImportPreviewResult fileError(String filename, String message) {
        return new ImportPreviewResult(null, filename, message, 0, 0, 0, 0, 0, 0, List.of());
    }

    public boolean hasFileLevelError() {
        return fileLevelError != null;
    }

    public boolean canCommit() {
        return !hasFileLevelError() && errorCount == 0 && content != null;
    }
}
