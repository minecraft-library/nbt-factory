package dev.sbs.minecraftapi.nbt.tags.primitive;

import dev.sbs.minecraftapi.nbt.tags.TagType;
import org.jetbrains.annotations.NotNull;

/**
 * {@link TagType#LONG} (ID 4) is used for storing a signed 64-bit integer, ranging from {@link Long#MIN_VALUE} to {@link Long#MAX_VALUE} (inclusive).
 */
public class LongTag extends NumericalTag<Long> {

    public static final @NotNull LongTag EMPTY = new LongTag() {
        @Override
        public void setValue(@NotNull Long value) {
            throw new UnsupportedOperationException("This nbt tag is not modifiable.");
        }
    };

    /**
     * Constructs a long tag with a 0 value.
     */
    public LongTag() {
        this(0);
    }

    /**
     * Constructs a long tag with a given value.
     *
     * @param value the tag's value, to be converted to {@code long}.
     */
    public LongTag(@NotNull Number value) {
        super(value.longValue());
    }

    @Override
    public final @NotNull LongTag clone() {
        return new LongTag(this.getValue());
    }

    @Override
    public final byte getId() {
        return TagType.LONG.getId();
    }

}
