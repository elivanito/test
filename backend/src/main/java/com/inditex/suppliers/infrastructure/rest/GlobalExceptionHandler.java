package com.inditex.suppliers.infrastructure.rest;

import com.inditex.suppliers.domain.exception.CandidateAlreadyExistsException;
import com.inditex.suppliers.domain.exception.CandidateNotFoundException;
import com.inditex.suppliers.domain.exception.CandidateNotPendingException;
import com.inditex.suppliers.domain.exception.CountryBannedException;
import com.inditex.suppliers.domain.exception.CountryUnknownException;
import com.inditex.suppliers.domain.exception.DomainException;
import com.inditex.suppliers.domain.exception.InsufficientTurnoverException;
import com.inditex.suppliers.domain.exception.InvalidAnnualTurnoverException;
import com.inditex.suppliers.domain.exception.InvalidCountryCodeException;
import com.inditex.suppliers.domain.exception.InvalidDunsException;
import com.inditex.suppliers.domain.exception.SupplierBannedException;
import com.inditex.suppliers.domain.exception.SupplierCannotBeBannedException;
import com.inditex.suppliers.domain.exception.SupplierNotFoundException;
import com.inditex.suppliers.infrastructure.rest.dto.ErrorDto;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // ---------- 400 Bad Request ----------
    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class,
            InvalidDunsException.class,
            InvalidCountryCodeException.class,
            InvalidAnnualTurnoverException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ErrorDto> badRequest(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorDto(ex.getMessage() == null ? "Bad Request" : ex.getMessage()));
    }

    // ---------- 404 Not Found ----------
    @ExceptionHandler({CandidateNotFoundException.class, SupplierNotFoundException.class})
    public ResponseEntity<ErrorDto> notFound(DomainException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    // ---------- 409 Conflict ----------
    @ExceptionHandler({
            CandidateAlreadyExistsException.class,
            SupplierBannedException.class,
            CandidateNotPendingException.class,
            SupplierCannotBeBannedException.class
    })
    public ResponseEntity<ErrorDto> conflict(DomainException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorDto(ex.getMessage()));
    }

    /**
     * Optimistic-lock failure: two concurrent transactions tried to update the same
     * aggregate and the second one lost the race. The caller should refetch and retry.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorDto> optimisticLock(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorDto("Concurrent modification detected — please retry"));
    }

    // ---------- 422 Unprocessable Content ----------
    @ExceptionHandler({
            CountryBannedException.class,
            InsufficientTurnoverException.class,
            CountryUnknownException.class
    })
    public ResponseEntity<ErrorDto> unprocessable(DomainException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorDto(ex.getMessage()));
    }

    // ---------- 500 Fallback ----------
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDto> generic(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorDto("Internal error"));
    }
}
