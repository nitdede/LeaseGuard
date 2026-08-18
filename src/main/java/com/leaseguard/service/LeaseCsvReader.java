package com.leaseguard.service;

import com.leaseguard.exception.ImportFileFormatException;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * Parses the denormalized lease-portfolio CSV into raw, untyped rows. Field-level and
 * cross-field validation happens afterward in {@link CsvRowValidator} and {@link ImportService};
 * this class only owns CSV structure (header presence, column splitting).
 */
@Component
class LeaseCsvReader {

    List<RawCsvRow> read(Reader reader) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setIgnoreSurroundingSpaces(true)
                .get();

        try (CSVParser parser = format.parse(reader)) {
            List<String> headerNames = parser.getHeaderNames();
            List<String> missing = LeaseCsvColumns.REQUIRED_HEADERS.stream()
                    .filter(column -> !headerNames.contains(column))
                    .toList();
            if (!missing.isEmpty()) {
                throw new ImportFileFormatException(
                        "CSV is missing required column(s): " + String.join(", ", missing));
            }

            List<RawCsvRow> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                Map<String, String> fields = new LinkedHashMap<>();
                for (String column : LeaseCsvColumns.REQUIRED_HEADERS) {
                    fields.put(column, record.get(column));
                }
                // +1 because commons-csv record numbers are 1-based over data rows only,
                // and the header occupies line 1 of the file.
                rows.add(new RawCsvRow((int) record.getRecordNumber() + 1, fields));
            }
            if (rows.isEmpty()) {
                throw new ImportFileFormatException("CSV file has no data rows.");
            }
            return rows;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded CSV file.", e);
        } catch (IllegalStateException | ArrayIndexOutOfBoundsException e) {
            throw new ImportFileFormatException("CSV file is malformed: " + e.getMessage(), e);
        }
    }
}
