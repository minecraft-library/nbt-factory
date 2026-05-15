package lib.minecraft.nbt.tags.primitive;

import lib.minecraft.nbt.tags.Tag;
import lib.minecraft.nbt.tags.TagType;
import org.jetbrains.annotations.NotNull;

/**
 * {@link TagType#INT} (ID 3) is used for storing a signed 32-bit integer, ranging from {@link Integer#MIN_VALUE} and {@link Integer#MAX_VALUE} (inclusive).
 */
public class IntTag extends NumericalTag<Integer> {

    public static final @NotNull IntTag EMPTY = new IntTag() {
        @Override
        public void setValue(@NotNull Integer value) {
            throw new UnsupportedOperationException("This nbt tag is not modifiable.");
        }
    };

    private static final int CACHE_LOW = -128;

    private static final int CACHE_HIGH = 127;

    /**
     * Box cache covering {@link #CACHE_LOW}..{@link #CACHE_HIGH} inclusive. Mirrors
     * {@link Integer#valueOf(int)}'s box cache so repeated reads of common NBT integer values
     * (enchantment levels, slot indices, small counts) share a single instance across the parser
     * hot path.
     *
     * <p>Cache entries are regular {@code IntTag} instances - the {@link #EMPTY} sentinel is not
     * slotted here, because {@link #EMPTY} is an anonymous subclass and {@link Tag#equals(Object)}
     * compares classes, so substituting it into a parsed tree would break round-trip equality
     * against fixtures that use {@code new IntTag(0)}.</p>
     */
    private static final @NotNull IntTag @NotNull [] CACHE;

    static {
        int span = CACHE_HIGH - CACHE_LOW + 1;
        IntTag[] arr = new IntTag[span];

        for (int i = 0; i < span; i++)
            arr[i] = new IntTag(CACHE_LOW + i);

        CACHE = arr;
    }

    /**
     * Returns a possibly-shared {@code IntTag} for {@code value}. Values in
     * {@code [-128, 127]} are interned; values outside that range allocate a fresh tag.
     *
     * <p>Mirrors {@link Integer#valueOf(int)}: behaviorally identical to
     * {@code new IntTag(value)}, but reuses cached instances for common values to cut the
     * allocation rate of the materializing decoder.</p>
     *
     * <p><b>Mutation hazard</b> - cached instances are mutable. Calling
     * {@link #setValue(Integer)} on a tag returned from {@code of(...)} mutates the shared cache
     * entry, so every other holder of the same value observes the change. Use
     * {@code new IntTag(value)} when the caller intends to mutate.</p>
     *
     * <p>Note: {@link #EMPTY} is not part of the cache - {@code IntTag.of(0)} returns a regular
     * mutable cached instance, not the immutable sentinel.</p>
     *
     * @param value the integer value
     * @return a possibly-shared int tag holding {@code value}
     */
    public static @NotNull IntTag of(int value) {
        if (value >= CACHE_LOW && value <= CACHE_HIGH)
            return CACHE[value - CACHE_LOW];

        return new IntTag(value);
    }

    /**
     * Constructs an int tag with a 0 value.
     */
    public IntTag() {
        this(0);
    }

    /**
     * Constructs an int tag with the given primitive {@code int} value.
     *
     * <p>Primitive overload - avoids the duplicate autobox the {@link #IntTag(Number)} path
     * incurred when called from the NBT read dispatcher with a primitive argument.</p>
     *
     * @param value the tag's primitive {@code int} value
     */
    public IntTag(int value) {
        super(value);
    }

    /**
     * Constructs an int tag with a given value.
     *
     * @param value the tag's value, to be converted to {@code int}
     */
    public IntTag(@NotNull Number value) {
        this(value.intValue());
    }

    @Override
    public final @NotNull IntTag clone() {
        return new IntTag(this.getValue());
    }

    @Override
    public final byte getId() {
        return TagType.INT.getId();
    }

}
