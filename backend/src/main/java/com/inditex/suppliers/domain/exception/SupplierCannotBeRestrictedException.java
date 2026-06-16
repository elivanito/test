package com.inditex.suppliers.domain.exception;

/** FSM violation: only ACTIVE suppliers can be restricted (→ ON_PROBATION). */
public class SupplierCannotBeRestrictedException extends DomainException {
    public SupplierCannotBeRestrictedException() {
        super("Supplier can not be restricted");
    }
}
