package com.leaseguard.dto;

import java.time.Instant;

public record ImportCommitResult(Long batchId, String filename, int successCount, Instant importedAt) {
}
