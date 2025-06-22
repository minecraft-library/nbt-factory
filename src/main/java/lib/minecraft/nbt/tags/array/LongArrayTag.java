package dev.sbs.minecraftapi.nbt.tags.array;

import dev.sbs.minecraftapi.nbt.tags.TagType;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

/**
 * {@link TagType#LONG_ARRAY} (ID 12) is used for storing an ordered list of 64-bit integers.
 */
public class LongArrayTag extends ArrayTag<Long> {

    public static final @NotNull LongArrayTag EMPTY = new LongArrayTag(new Long[0]) {
        @Override
        protected void requireModifiable() {
            throw new UnsupportedOperationException("This nbt tag is not modifiable.");
        }
    };

    /**
     * Constructs an empty long array.
     */
    public LongArrayTag() {
        this(new Long[0]);
    }

    /**
     * Constructs an unnamed long array tag using a {@code List<>} object.
     *
     * @param value the tag's {@code List<>} value, to be converted to a primitive {@code long[]} array.
     */
    public LongArrayTag(@NotNull Collection<Long> value) {
        this(value.toArray(new Long[0]));
    }

    /**
     * Constructs an unnamed long array tag using an array object.
     *
     * @param value the tag's long[] value.
     */
    public LongArrayTag(@NotNull Long... value) {
        super(value);
    }

    @Override
    public final @NotNull LongArrayTag clone() {
        return new LongArrayTag(Arrays.copyOf(this.getValue(), this.length()));
    }

    @Override
    public final byte getId() {
        return TagType.LONG_ARRAY.getId();
    }

}
