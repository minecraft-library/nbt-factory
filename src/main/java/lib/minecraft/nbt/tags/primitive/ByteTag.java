package dev.sbs.minecraftapi.nbt.tags.primitive;

import dev.sbs.minecraftapi.nbt.tags.TagType;
import org.jetbrains.annotations.NotNull;

/**
 * {@link TagType#BYTE} (ID 1) is used for storing a signed 8-bit integer, ranging from {@link Byte#MIN_VALUE} to {@link Byte#MAX_VALUE} (inclusive).
 */
public class ByteTag extends NumericalTag<Byte> {

    public static final @NotNull ByteTag EMPTY = new ByteTag() {
        @Override
        public void setValue(@NotNull Byte value) {
            throw new UnsupportedOperationException("This nbt tag is not modifiable.");
        }
    };

    /**
     * Constructs a byte tag with a 0 value.
     */
    public ByteTag() {
        this((byte) 0);
    }

    /**
     * Constructs a byte tag with the given primitive {@code byte} value.
     *
     * <p>Primitive overload - avoids the duplicate autobox the {@link #ByteTag(Number)} path
     * incurred when called from the NBT read dispatcher with a primitive argument.</p>
     *
     * @param value the tag's primitive {@code byte} value
     */
    public ByteTag(byte value) {
        super(value);
    }

    /**
     * Constructs a byte tag with a given value.
     *
     * @param value the tag's value, to be converted to {@code byte}.
     */
    public ByteTag(@NotNull Number value) {
        this(value.byteValue());
    }

    @Override
    public final @NotNull ByteTag clone() {
        return new ByteTag(this.getValue());
    }

    @Override
    public final byte getId() {
        return TagType.BYTE.getId();
    }

}
