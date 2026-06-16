package com.inditex.suppliers.domain.exception;

public class SupplierBannedException extends DomainException {
    public SupplierBannedException() {
        super("Supplier banned");
    }
}
