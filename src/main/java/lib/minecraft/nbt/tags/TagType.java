package lib.minecraft.nbt.tags;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * Defines the 14 NBT tag types - the 13 wire types from the Minecraft NBT format plus the
 * synthetic {@link BooleanTag}, which encodes as {@code TAG_Byte} on the wire.
 */
@Getter
public enum TagType {

    /**
     * ID: 0
     *
     * @see EndTag
     */
    END((byte) 0, EndTag.class),
    /**
     * ID: 1
     *
     * @see ByteTag
     */
    BYTE((byte) 1, ByteTag.class),
    /**
     * ID: 2
     *
     * @see ShortTag
     */
    SHORT((byte) 2, ShortTag.class),
    /**
     * ID: 3
     *
     * @see IntTag
     */
    INT((byte) 3, IntTag.class),
    /**
     * ID: 4
     *
     * @see LongTag
     */
    LONG((byte) 4, LongTag.class),
    /**
     * ID: 5
     *
     * @see FloatTag
     */
    FLOAT((byte) 5, FloatTag.class),
    /**
     * ID: 6
     *
     * @see DoubleTag
     */
    DOUBLE((byte) 6, DoubleTag.class),
    /**
     * ID: 7
     *
     * @see ByteArrayTag
     */
    BYTE_ARRAY((byte) 7, ByteArrayTag.class),
    /**
     * ID: 8
     *
     * @see StringTag
     */
    STRING((byte) 8, StringTag.class),
    /**
     * ID: 9
     *
     * @see ListTag
     */
    LIST((byte) 9, ListTag.class),
    /**
     * ID: 10
     *
     * @see CompoundTag
     */
    COMPOUND((byte) 10, CompoundTag.class),
    /**
     * ID: 11
     *
     * @see IntArrayTag
     */
    INT_ARRAY((byte) 11, IntArrayTag.class),
    /**
     * ID: 12
     *
     * @see LongArrayTag
     */
    LONG_ARRAY((byte) 12, LongArrayTag.class);

    static final TagType[] VALUES;

    private static final TagType[] BY_ID;

    static {
        VALUES = values();
        BY_ID = new TagType[13];

        for (TagType t : VALUES)
            BY_ID[t.id] = t;
    }

    private final byte id;
    private final @NotNull Class<? extends Tag> tagClass;

    TagType(byte id, @NotNull Class<? extends Tag> tagClass) {
        this.id = id;
        this.tagClass = tagClass;
    }

    /**
     * Returns the {@code TagType} whose wire id matches {@code id}.
     *
     * @throws IllegalArgumentException if {@code id} is not a valid NBT tag id
     */
    public static @NotNull TagType byId(byte id) {
        if (id < 0 || id >= BY_ID.length)
            throw new IllegalArgumentException("Invalid NBT tag id: " + id);

        return BY_ID[id];
    }

}
