package com.leaseguard.dto;

/** One row/field-level problem found during CSV validation, shown verbatim in the preview UI. */
public record ImportIssue(int rowNumber, String fieldName, String rejectedValue, String message, IssueSeverity severity) {

    public static ImportIssue error(int rowNumber, String fieldName, String rejectedValue, String message) {
        return new ImportIssue(rowNumber, fieldName, rejectedValue, message, IssueSeverity.ERROR);
    }

    public static ImportIssue warning(int rowNumber, String fieldName, String rejectedValue, String message) {
        return new ImportIssue(rowNumber, fieldName, rejectedValue, message, IssueSeverity.WARNING);
    }
}
