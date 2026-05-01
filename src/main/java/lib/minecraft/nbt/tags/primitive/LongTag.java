package lib.minecraft.nbt.tags.primitive;

import lib.minecraft.nbt.tags.Tag;
import lib.minecraft.nbt.tags.TagType;
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

    private static final long CACHE_LOW = -128L;

    private static final long CACHE_HIGH = 127L;

    /**
     * Box cache covering {@code [-128, 127]} inclusive. Range matches
     * {@link Long#valueOf(long)}'s box cache.
     *
     * <p>Cache entries are regular {@code LongTag} instances - the {@link #EMPTY} sentinel is not
     * slotted here, because {@link #EMPTY} is an anonymous subclass and
     * {@link Tag#equals(Object)} compares classes, so substituting it into a parsed tree would
     * break round-trip equality against fixtures that use {@code new LongTag(0L)}.</p>
     */
    private static final @NotNull LongTag @NotNull [] CACHE;

    static {
        int span = (int) (CACHE_HIGH - CACHE_LOW + 1);
        LongTag[] arr = new LongTag[span];

        for (int i = 0; i < span; i++)
            arr[i] = new LongTag(CACHE_LOW + i);

        CACHE = arr;
    }

    /**
     * Returns a possibly-shared {@code LongTag} for {@code value}. Values in
     * {@code [-128, 127]} are interned; values outside that range allocate a fresh tag.
     *
     * <p><b>Mutation hazard</b> - cached instances are mutable. Calling {@link #setValue(Long)}
     * on a tag returned from {@code of(...)} mutates the shared cache entry, so every other holder
     * of the same value observes the change. Use {@code new LongTag(value)} when the caller
     * intends to mutate.</p>
     *
     * <p>Note: {@link #EMPTY} is not part of the cache - {@code LongTag.of(0L)} returns a regular
     * mutable cached instance, not the immutable sentinel.</p>
     *
     * @param value the long value
     * @return a possibly-shared long tag holding {@code value}
     */
    public static @NotNull LongTag of(long value) {
        if (value >= CACHE_LOW && value <= CACHE_HIGH)
            return CACHE[(int) (value - CACHE_LOW)];

        return new LongTag(value);
    }

    /**
     * Constructs a long tag with a 0 value.
     */
    public LongTag() {
        this(0L);
    }

    /**
     * Constructs a long tag with the given primitive {@code long} value.
     *
     * <p>Primitive overload - avoids the duplicate autobox the {@link #LongTag(Number)} path
     * incurred when called from the NBT read dispatcher with a primitive argument.</p>
     *
     * @param value the tag's primitive {@code long} value
     */
    public LongTag(long value) {
        super(value);
    }

    /**
     * Constructs a long tag with a given value.
     *
     * @param value the tag's value, to be converted to {@code long}.
     */
    public LongTag(@NotNull Number value) {
        this(value.longValue());
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
