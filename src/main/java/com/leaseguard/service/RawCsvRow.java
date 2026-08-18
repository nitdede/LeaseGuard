package com.leaseguard.service;

import java.util.Map;

/** One raw CSV data row: 1-based row number (as the file's line number, header is row 1) and its named field values. */
public record RawCsvRow(int rowNumber, Map<String, String> fields) {

    /** Returns the trimmed value for a column, or null if blank/absent (empty optional strings normalize to null). */
    public String get(String column) {
        String value = fields.get(column);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
