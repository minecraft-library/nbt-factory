package lib.minecraft.nbt.tags.primitive;

import lib.minecraft.nbt.tags.Tag;
import lib.minecraft.nbt.tags.TagType;
import org.jetbrains.annotations.NotNull;

/**
 * {@link TagType#SHORT} (ID 2) is used for storing a signed 16-bit integer, ranging from {@link Short#MIN_VALUE} to {@link Short#MAX_VALUE} (inclusive).
 */
public class ShortTag extends NumericalTag<Short> {

    public static final @NotNull ShortTag EMPTY = new ShortTag() {
        @Override
        public void setValue(@NotNull Short value) {
            throw new UnsupportedOperationException("This nbt tag is not modifiable.");
        }
    };

    private static final int CACHE_LOW = -128;

    private static final int CACHE_HIGH = 127;

    /**
     * Box cache covering {@link #CACHE_LOW}..{@link #CACHE_HIGH} inclusive. Mirrors the cache
     * layout of {@link IntTag} - small shorts dominate the materializing-decode workload (slot
     * counts, damage values, item ids in legacy NBT).
     *
     * <p>Cache entries are regular {@code ShortTag} instances - the {@link #EMPTY} sentinel is
     * not slotted here, because {@link #EMPTY} is an anonymous subclass and
     * {@link Tag#equals(Object)} compares classes, so substituting it into a parsed tree would
     * break round-trip equality against fixtures that use {@code new ShortTag((short) 0)}.</p>
     */
    private static final @NotNull ShortTag @NotNull [] CACHE;

    static {
        int span = CACHE_HIGH - CACHE_LOW + 1;
        ShortTag[] arr = new ShortTag[span];

        for (int i = 0; i < span; i++)
            arr[i] = new ShortTag((short) (CACHE_LOW + i));

        CACHE = arr;
    }

    /**
     * Returns a possibly-shared {@code ShortTag} for {@code value}. Values in
     * {@code [-128, 127]} are interned; values outside that range allocate a fresh tag.
     *
     * <p><b>Mutation hazard</b> - cached instances are mutable. Calling {@link #setValue(Short)}
     * on a tag returned from {@code of(...)} mutates the shared cache entry, so every other holder
     * of the same value observes the change. Use {@code new ShortTag(value)} when the caller
     * intends to mutate.</p>
     *
     * <p>Note: {@link #EMPTY} is not part of the cache - {@code ShortTag.of((short) 0)} returns a
     * regular mutable cached instance, not the immutable sentinel.</p>
     *
     * @param value the short value
     * @return a possibly-shared short tag holding {@code value}
     */
    public static @NotNull ShortTag of(short value) {
        if (value >= CACHE_LOW && value <= CACHE_HIGH)
            return CACHE[value - CACHE_LOW];

        return new ShortTag(value);
    }

    /**
     * Constructs a short tag with a 0 value.
     */
    public ShortTag() {
        this((short) 0);
    }

    /**
     * Constructs a short tag with the given primitive {@code short} value.
     *
     * <p>Primitive overload - avoids the duplicate autobox the {@link #ShortTag(Number)} path
     * incurred when called from the NBT read dispatcher with a primitive argument.</p>
     *
     * @param value the tag's primitive {@code short} value
     */
    public ShortTag(short value) {
        super(value);
    }

    /**
     * Constructs a short tag with a given value.
     *
     * @param value the tag's value, to be converted to {@code short}
     */
    public ShortTag(@NotNull Number value) {
        this(value.shortValue());
    }

    @Override
    public final @NotNull ShortTag clone() {
        return new ShortTag(this.getValue());
    }

    @Override
    public final byte getId() {
        return TagType.SHORT.getId();
    }

}
