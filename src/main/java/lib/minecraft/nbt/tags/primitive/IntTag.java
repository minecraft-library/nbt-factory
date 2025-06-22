package dev.sbs.minecraftapi.nbt.tags.primitive;

import dev.sbs.minecraftapi.nbt.tags.TagType;
import org.jetbrains.annotations.NotNull;

/**
 * {@link TagType#INT} (ID 3) is used for storing a signed 32-bit integer, ranging from {@link Integer#MIN_VALUE} and {@link Integer#MAX_VALUE} (inclusive).
 */
public class IntTag extends NumericalTag<Integer> {

    public static final @NotNull IntTag EMPTY = new IntTag() {
        @Override
        public void setValue(@NotNull Integer value) {
            throw new UnsupportedOperationException("This nbt tag is not modifiable.");
        }
    };

    /**
     * Constructs an int tag with a 0 value.
     */
    public IntTag() {
        this(0);
    }

    /**
     * Constructs an int tag with a given value.
     *
     * @param value the tag's value, to be converted to {@code int}.
     */
    public IntTag(@NotNull Number value) {
        super(value.intValue());
    }

    @Override
    public final @NotNull IntTag clone() {
        return new IntTag(this.getValue());
    }

    @Override
    public final byte getId() {
        return TagType.INT.getId();
    }

}
