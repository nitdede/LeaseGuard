package com.leaseguard.dto;

/** Per-row preview outcome shown to the user before committing an import. */
public enum RowStatus {
    NEW,
    CHANGED,
    UNCHANGED,
    WARNING,
    ERROR
}
