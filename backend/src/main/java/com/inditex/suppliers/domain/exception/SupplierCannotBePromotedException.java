package com.inditex.suppliers.domain.exception;

/** FSM violation: only ON_PROBATION suppliers can be promoted (→ ACTIVE). */
public class SupplierCannotBePromotedException extends DomainException {
    public SupplierCannotBePromotedException() {
        super("Supplier can not be promoted");
    }
}
