package com.inditex.suppliers.infrastructure.persistence.mapper;

/**
 * Generic name-based enum converter.
 * Eliminates the four identical {@code X.valueOf(y.name())} helper methods
 * previously scattered in {@link PersistenceMapper}.
 */
public final class EnumMapper {

    private EnumMapper() {}

    /** Maps an enum constant to the constant of the same name in another enum type. */
    public static <T extends Enum<T>> T map(Enum<?> source, Class<T> targetType) {
        return Enum.valueOf(targetType, source.name());
    }
}
