package com.leaseguard.exception;

/** Thrown when the uploaded file is not parseable as CSV or is missing required columns. */
public class ImportFileFormatException extends RuntimeException {

    public ImportFileFormatException(String message) {
        super(message);
    }

    public ImportFileFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
