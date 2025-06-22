package dev.sbs.minecraftapi.nbt.tags.array;

import dev.sbs.minecraftapi.nbt.tags.TagType;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

/**
 * {@link TagType#BYTE_ARRAY} (ID 7) is used for storing an ordered list of 8-bit integers.
 */
public class ByteArrayTag extends ArrayTag<Byte> {

    public static final @NotNull ByteArrayTag EMPTY = new ByteArrayTag(new Byte[0]) {
        @Override
        protected void requireModifiable() {
            throw new UnsupportedOperationException("This nbt tag is not modifiable.");
        }
    };

    /**
     * Constructs an empty byte array.
     */
    public ByteArrayTag() {
        this(new Byte[0]);
    }

    /**
     * Constructs an unnamed byte array tag using a {@code List<>} object.
     *
     * @param value the tag's {@code List<>} value, to be converted to a primitive {@code byte[]} array.
     */
    public ByteArrayTag(@NotNull Collection<Byte> value) {
        this(value.toArray(new Byte[0]));
    }

    /**
     * Constructs an unnamed byte array tag using an array object.
     *
     * @param value the tag's byte[] value.
     */
    public ByteArrayTag(@NotNull Byte... value) {
        super(value);
    }

    @Override
    public final byte getId() {
        return TagType.BYTE_ARRAY.getId();
    }

    @Override
    public final @NotNull ByteArrayTag clone() {
        return new ByteArrayTag(Arrays.copyOf(this.getValue(), this.length()));
    }

}
