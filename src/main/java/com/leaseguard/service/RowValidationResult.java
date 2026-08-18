package com.leaseguard.service;

import com.leaseguard.dto.ImportIssue;
import java.util.List;

record RowValidationResult(int rowNumber, ParsedLeaseRow parsedRow, List<ImportIssue> errors) {

    boolean isValid() {
        return errors.isEmpty();
    }
}
