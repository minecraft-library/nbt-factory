package lib.minecraft.nbt.tags;

import lib.minecraft.nbt.tags.array.ByteArrayTag;
import lib.minecraft.nbt.tags.array.IntArrayTag;
import lib.minecraft.nbt.tags.array.LongArrayTag;
import lib.minecraft.nbt.tags.collection.CompoundTag;
import lib.minecraft.nbt.tags.collection.ListTag;
import lib.minecraft.nbt.tags.primitive.ByteTag;
import lib.minecraft.nbt.tags.primitive.DoubleTag;
import lib.minecraft.nbt.tags.primitive.EndTag;
import lib.minecraft.nbt.tags.primitive.FloatTag;
import lib.minecraft.nbt.tags.primitive.IntTag;
import lib.minecraft.nbt.tags.primitive.LongTag;
import lib.minecraft.nbt.tags.primitive.ShortTag;
import lib.minecraft.nbt.tags.primitive.StringTag;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Defines the 13 Minecraft NBT tag types.
 */
@Getter
public enum TagType {

    /**
     * ID: 0
     *
     * @see EndTag
     */
    END(0, Void.class, EndTag.class),
    /**
     * ID: 1
     *
     * @see ByteTag
     */
    BYTE(1, Byte.class, ByteTag.class),
    /**
     * ID: 2
     *
     * @see ShortTag
     */
    SHORT(2, Short.class, ShortTag.class),
    /**
     * ID: 3
     *
     * @see IntTag
     */
    INT(3, Integer.class, IntTag.class),
    /**
     * ID: 4
     *
     * @see LongTag
     */
    LONG(4, Long.class, LongTag.class),
    /**
     * ID: 5
     *
     * @see FloatTag
     */
    FLOAT(5, Float.class, FloatTag.class),
    /**
     * ID: 6
     *
     * @see DoubleTag
     */
    DOUBLE(6, Double.class, DoubleTag.class),
    /**
     * ID: 7
     *
     * @see ByteArrayTag
     */
    BYTE_ARRAY(7, byte[].class, ByteArrayTag.class),
    /**
     * ID: 8
     *
     * @see StringTag
     */
    STRING(8, String.class, StringTag.class),
    /**
     * ID: 9
     *
     * @see ListTag
     */
    LIST(9, List.class, ListTag.class),
    /**
     * ID: 10
     *
     * @see CompoundTag
     */
    COMPOUND(10, Map.class, CompoundTag.class),
    /**
     * ID: 11
     *
     * @see IntArrayTag
     */
    INT_ARRAY(11, int[].class, IntArrayTag.class),
    /**
     * ID: 12
     *
     * @see LongArrayTag
     */
    LONG_ARRAY(12, long[].class, LongArrayTag.class);

    static final TagType[] VALUES;

    static {
        VALUES = values();
    }

    private final byte id;
    private final @NotNull Class<?> javaClass;
    private final @NotNull Class<? extends Tag<?>> tagClass;

    <J, T extends Tag<J>> TagType(int id, @NotNull Class<? super J> javaClass, @NotNull Class<T> tagClass) {
        this.id = (byte) id;
        this.javaClass = javaClass;
        this.tagClass = tagClass;
    }

}
