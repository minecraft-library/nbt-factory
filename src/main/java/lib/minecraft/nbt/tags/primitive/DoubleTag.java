package dev.sbs.minecraftapi.nbt.tags.primitive;

import dev.sbs.minecraftapi.nbt.tags.TagType;
import org.jetbrains.annotations.NotNull;

/**
 * {@link TagType#DOUBLE} (ID 6) is used for storing a 64-bit, double-precision floating-point, ranging from {@link Double#MIN_VALUE} to {@link Double#MAX_VALUE}.
 * @see <a href="https://en.wikipedia.org/wiki/IEEE_floating_point">IEEE_floating_point</a>
 */
public class DoubleTag extends NumericalTag<Double> {

    public static final @NotNull DoubleTag EMPTY = new DoubleTag() {
        @Override
        public void setValue(@NotNull Double value) {
            throw new UnsupportedOperationException("This nbt tag is not modifiable.");
        }
    };

    /**
     * Constructs a double tag with a 0 value.
     */
    public DoubleTag() {
        this(0.0d);
    }

    /**
     * Constructs a double tag with the given primitive {@code double} value.
     *
     * <p>Primitive overload - avoids the duplicate autobox the {@link #DoubleTag(Number)} path
     * incurred when called from the NBT read dispatcher with a primitive argument. {@code Double}
     * has no wrapper cache, so every call through the {@code Number} path previously allocated
     * two {@code Double} objects per tag read.</p>
     *
     * @param value the tag's primitive {@code double} value
     */
    public DoubleTag(double value) {
        super(value);
    }

    /**
     * Constructs a double tag with a given value.
     *
     * @param value the tag's value, to be converted to {@code double}.
     */
    public DoubleTag(@NotNull Number value) {
        this(value.doubleValue());
    }

    @Override
    public final @NotNull DoubleTag clone() {
        return new DoubleTag(this.getValue());
    }

    @Override
    public final byte getId() {
        return TagType.DOUBLE.getId();
    }

}
