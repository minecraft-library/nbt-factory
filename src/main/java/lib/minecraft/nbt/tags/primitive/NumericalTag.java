package lib.minecraft.nbt.tags.primitive;

import lib.minecraft.nbt.tags.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * An abstract superclass of all {@link Tag Tags} representing numeric values that can be converted to the primitive types.
 *
 * @param <T> the {@code Number} type this NBT tag represents
 */
public abstract class NumericalTag<T extends Number & Comparable<T>> extends Tag<T> {

    protected NumericalTag(@NotNull T value) {
        super(value);
    }

    protected NumericalTag(@NotNull Supplier<T> supplier) {
        super(supplier);
    }

    /**
     * Returns the value held by this tag as a primitive {@code byte}.
     *
     * @return the value held by this tag as a primitive {@code byte}
     */
    public byte byteValue() {
        return this.getValue().byteValue();
    }

    @Override
    public abstract @NotNull NumericalTag<T> clone();

    /**
     * Returns the value held by this tag as a primitive {@code double}.
     *
     * <p>Borrow subclasses override this to read the value directly from the underlying tape,
     * skipping the {@code getValue()} unbox path.</p>
     *
     * @return the value held by this tag as a primitive {@code double}
     */
    public double doubleValue() {
        return this.getValue().doubleValue();
    }

    /**
     * Returns the value held by this tag as a primitive {@code float}.
     *
     * <p>Borrow subclasses override this to read the value directly from the underlying tape,
     * skipping the {@code getValue()} unbox path.</p>
     *
     * @return the value held by this tag as a primitive {@code float}
     */
    public float floatValue() {
        return this.getValue().floatValue();
    }

    /**
     * Returns the value held by this tag as a primitive {@code int}.
     *
     * <p>Borrow subclasses override this to read the value directly from the underlying tape,
     * skipping the {@code getValue()} unbox path.</p>
     *
     * @return the value held by this tag as a primitive {@code int}
     */
    public int intValue() {
        return this.getValue().intValue();
    }

    /**
     * Returns the value held by this tag as a primitive {@code long}.
     *
     * <p>Borrow subclasses override this to read the value directly from the underlying tape,
     * skipping the {@code getValue()} unbox path.</p>
     *
     * @return the value held by this tag as a primitive {@code long}
     */
    public long longValue() {
        return this.getValue().longValue();
    }

    /**
     * Returns the value held by this tag as a primitive {@code short}.
     *
     * <p>Borrow subclasses override this to read the value directly from the underlying tape,
     * skipping the {@code getValue()} unbox path.</p>
     *
     * @return the value held by this tag as a primitive {@code short}
     */
    public short shortValue() {
        return this.getValue().shortValue();
    }

    @Override
    public final @NotNull String toString() {
        return this.getValue().toString();
    }

}
