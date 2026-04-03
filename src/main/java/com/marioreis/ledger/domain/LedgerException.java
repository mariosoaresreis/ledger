package com.marioreis.ledger.domain;

import org.springframework.http.HttpStatus;

public class LedgerException extends RuntimeException {

    private final HttpStatus status;

    private LedgerException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static LedgerException notFound(String message) {
        return new LedgerException(HttpStatus.NOT_FOUND, message);
    }

    public static LedgerException conflict(String message) {
        return new LedgerException(HttpStatus.CONFLICT, message);
    }

    public static LedgerException unprocessable(String message) {
        return new LedgerException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    public HttpStatus status() {
        return status;
    }
}

