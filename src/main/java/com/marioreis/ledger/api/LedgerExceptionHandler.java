package com.marioreis.ledger.api;

import com.marioreis.ledger.domain.LedgerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class LedgerExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(LedgerExceptionHandler.class);

    @ExceptionHandler(LedgerException.class)
    public ProblemDetail handleLedgerException(LedgerException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problemDetail.setTitle(exception.status().getReasonPhrase());
        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problemDetail.setTitle("Validation failed");
        return problemDetail;
    }

    @ExceptionHandler({MissingRequestHeaderException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail handleBadRequest(Exception exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problemDetail.setTitle("Bad request");
        return problemDetail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        log.error("Unexpected error while processing ledger command", exception);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred while processing the ledger command");
        problemDetail.setTitle("Internal Server Error");
        return problemDetail;
    }
}


