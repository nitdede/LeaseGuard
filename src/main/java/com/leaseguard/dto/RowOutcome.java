package com.leaseguard.dto;

import java.util.List;

public record RowOutcome(int rowNumber, String leaseExternalId, RowStatus status, List<ImportIssue> issues) {
}
