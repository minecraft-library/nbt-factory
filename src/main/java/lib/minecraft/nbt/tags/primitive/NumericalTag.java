package dev.sbs.minecraftapi.nbt.tags.primitive;

import dev.sbs.minecraftapi.nbt.tags.Tag;
import org.jetbrains.annotations.NotNull;

/**
 * An abstract superclass of all {@link Tag Tags} representing numeric values that can be converted to the primitive types.
 *
 * @param <T> the {@code Number} type this NBT tag represents.
 */
public abstract class NumericalTag<T extends Number & Comparable<T>> extends Tag<T> {

    protected NumericalTag(@NotNull T value) {
        super(value);
    }

    /**
     * Returns the value held by this tag as a primitive {@code byte}.
     *
     * @return the value held by this tag as a primitive {@code byte}.
     */
    public final byte byteValue() {
        return this.getValue().byteValue();
    }

    @Override
    public abstract @NotNull NumericalTag<T> clone();

    /**
     * Returns the value held by this tag as a primitive {@code double}.
     *
     * @return the value held by this tag as a primitive {@code double}.
     */
    public final double doubleValue() {
        return this.getValue().doubleValue();
    }

    /**
     * Returns the value held by this tag as a primitive {@code float}.
     *
     * @return the value held by this tag as a primitive {@code float}.
     */
    public final float floatValue() {
        return this.getValue().floatValue();
    }

    /**
     * Returns the value held by this tag as a primitive {@code int}.
     *
     * @return the value held by this tag as a primitive {@code int}.
     */
    public final int intValue() {
        return this.getValue().intValue();
    }

    /**
     * Returns the value held by this tag as a primitive {@code long}.
     *
     * @return the value held by this tag as a primitive {@code long}.
     */
    public final long longValue() {
        return this.getValue().longValue();
    }

    /**
     * Returns the value held by this tag as a primitive {@code short}.
     *
     * @return the value held by this tag as a primitive {@code short}.
     */
    public final short shortValue() {
        return this.getValue().shortValue();
    }

    @Override
    public final @NotNull String toString() {
        return this.getValue().toString();
    }

}
