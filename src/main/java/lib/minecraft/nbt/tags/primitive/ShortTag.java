package dev.sbs.minecraftapi.nbt.tags.primitive;

import dev.sbs.minecraftapi.nbt.tags.TagType;
import org.jetbrains.annotations.NotNull;

/**
 * {@link TagType#SHORT} (ID 2) is used for storing a signed 16-bit integer, ranging from {@link Short#MIN_VALUE} to {@link Short#MAX_VALUE} (inclusive).
 */
public class ShortTag extends NumericalTag<Short> {

    public static final @NotNull ShortTag EMPTY = new ShortTag() {
        @Override
        public void setValue(@NotNull Short value) {
            throw new UnsupportedOperationException("This nbt tag is not modifiable.");
        }
    };

    /**
     * Constructs a short tag with a 0 value.
     */
    public ShortTag() {
        this((short) 0);
    }

    /**
     * Constructs a short tag with the given primitive {@code short} value.
     *
     * <p>Primitive overload - avoids the duplicate autobox the {@link #ShortTag(Number)} path
     * incurred when called from the NBT read dispatcher with a primitive argument.</p>
     *
     * @param value the tag's primitive {@code short} value
     */
    public ShortTag(short value) {
        super(value);
    }

    /**
     * Constructs a short tag with a given value.
     *
     * @param value the tag's value, to be converted to {@code short}.
     */
    public ShortTag(@NotNull Number value) {
        this(value.shortValue());
    }

    @Override
    public final @NotNull ShortTag clone() {
        return new ShortTag(this.getValue());
    }

    @Override
    public final byte getId() {
        return TagType.SHORT.getId();
    }

}
