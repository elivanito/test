package com.inditex.suppliers.domain.exception;

public class SupplierCannotBeBannedException extends DomainException {
    public SupplierCannotBeBannedException() {
        super("Supplier cannot be banned (only suppliers on probation can be banned)");
    }
}
