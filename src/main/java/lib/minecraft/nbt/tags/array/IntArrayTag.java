package dev.sbs.minecraftapi.nbt.tags.array;

import dev.sbs.minecraftapi.nbt.tags.TagType;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

/**
 * {@link TagType#INT_ARRAY} (ID 11) is used for storing an ordered list of 32-bit integers.
 */
public class IntArrayTag extends ArrayTag<Integer> {

    public static final @NotNull IntArrayTag EMPTY = new IntArrayTag(new Integer[0]) {
        @Override
        protected void requireModifiable() {
            throw new UnsupportedOperationException("This nbt tag is not modifiable.");
        }
    };

    /**
     * Constructs an empty int array.
     */
    public IntArrayTag() {
        this(new Integer[0]);
    }

    /**
     * Constructs an unnamed int array tag using a {@code List<>} object.
     *
     * @param value the tag's {@code List<>} value, to be converted to a primitive {@code int[]} array.
     */
    public IntArrayTag(@NotNull Collection<Integer> value) {
        this(value.toArray(new Integer[0]));
    }

    /**
     * Constructs an unnamed int array tag using an array object.
     *
     * @param value the tag's int[] value.
     */
    public IntArrayTag(@NotNull Integer... value) {
        super(value);
    }

    @Override
    public final @NotNull IntArrayTag clone() {
        return new IntArrayTag(Arrays.copyOf(this.getValue(), this.length()));
    }

    @Override
    public final byte getId() {
        return TagType.INT_ARRAY.getId();
    }

}
