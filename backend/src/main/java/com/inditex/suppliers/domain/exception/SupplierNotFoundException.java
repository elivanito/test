package com.inditex.suppliers.domain.exception;

public class SupplierNotFoundException extends DomainException {
    public SupplierNotFoundException(long duns) {
        super("Supplier not found: " + duns);
    }
}
