package lib.minecraft.nbt.tags.primitive;

import lib.minecraft.nbt.tags.Tag;
import lib.minecraft.nbt.tags.TagType;
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

    private static final int CACHE_LOW = -128;

    private static final int CACHE_HIGH = 127;

    /**
     * Box cache covering the full signed-byte range {@code [-128, 127]}. Every {@code byte} value
     * is cached, so {@link #of(byte)} never allocates after static initialization.
     *
     * <p>Cache entries are regular {@code ByteTag} instances - the {@link #EMPTY} sentinel is not
     * slotted here, because {@link #EMPTY} is an anonymous subclass and {@link Tag#equals(Object)}
     * compares classes, so substituting it into a parsed tree would break round-trip equality
     * against fixtures that use {@code new ByteTag((byte) 0)}.</p>
     */
    private static final @NotNull ByteTag @NotNull [] CACHE;

    static {
        int span = CACHE_HIGH - CACHE_LOW + 1;
        ByteTag[] arr = new ByteTag[span];

        for (int i = 0; i < span; i++)
            arr[i] = new ByteTag((byte) (CACHE_LOW + i));

        CACHE = arr;
    }

    /**
     * Returns a shared {@code ByteTag} for {@code value}. The cache covers the full signed-byte
     * range, so this method never allocates after class initialization.
     *
     * <p><b>Mutation hazard</b> - cached instances are mutable. Calling {@link #setValue(Byte)}
     * on a tag returned from {@code of(...)} mutates the shared cache entry, so every other holder
     * of the same value observes the change. Use {@code new ByteTag(value)} when the caller
     * intends to mutate.</p>
     *
     * <p>Note: {@link #EMPTY} is not part of the cache - {@code ByteTag.of((byte) 0)} returns a
     * regular mutable cached instance, not the immutable sentinel.</p>
     *
     * @param value the byte value
     * @return a shared byte tag holding {@code value}
     */
    public static @NotNull ByteTag of(byte value) {
        return CACHE[value - CACHE_LOW];
    }

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
     * @param value the tag's value, to be converted to {@code byte}
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
