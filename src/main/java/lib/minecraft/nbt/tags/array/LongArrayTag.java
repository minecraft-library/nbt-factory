package dev.sbs.minecraftapi.nbt.tags.array;

import dev.sbs.minecraftapi.nbt.tags.Tag;
import dev.sbs.minecraftapi.nbt.tags.TagType;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.LongStream;

/**
 * {@link TagType#LONG_ARRAY} (ID 12) is used for storing an ordered sequence of 64-bit signed integers.
 *
 * <p>Backed by a primitive {@code long[]} - no per-element boxing.</p>
 */
public class LongArrayTag extends Tag<long[]> implements Iterable<Long> {

    private static final long[] EMPTY_ARRAY = new long[0];

    public static final @NotNull LongArrayTag EMPTY = new LongArrayTag(EMPTY_ARRAY) {
        @Override
        public void setValue(long @NotNull [] value) {
            throw new UnsupportedOperationException("This nbt tag is not modifiable.");
        }
    };

    /**
     * Constructs an empty long array tag.
     */
    public LongArrayTag() {
        super(EMPTY_ARRAY);
    }

    /**
     * Constructs an unnamed long array tag wrapping the given primitive {@code long[]}.
     *
     * @param value the tag's primitive {@code long[]} value
     */
    public LongArrayTag(long @NotNull ... value) {
        super(value);
    }

    @Override
    public final byte getId() {
        return TagType.LONG_ARRAY.getId();
    }

    /**
     * Number of elements in this long array tag.
     */
    public final int length() {
        return this.getValue().length;
    }

    /**
     * Returns the long at the specified position in this array tag.
     *
     * @param index index of the element to return
     * @return the long at the specified position
     */
    public final long get(int index) {
        return this.getValue()[index];
    }

    /**
     * Replaces the element at the specified position with the given long.
     *
     * @param index index of the element to replace
     * @param element long to be stored at the specified position
     * @return the previous value at the specified position
     */
    public final long set(int index, long element) {
        long[] array = this.getValue();
        long previous = array[index];
        array[index] = element;
        return previous;
    }

    /**
     * Returns a sequential {@link LongStream} over the backing {@code long[]} - zero boxing.
     */
    public final @NotNull LongStream longStream() {
        return Arrays.stream(this.getValue());
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Arrays.equals(this.getValue(), ((LongArrayTag) o).getValue());
    }

    @Override
    public final int hashCode() {
        return Arrays.hashCode(this.getValue());
    }

    @Override
    public final @NotNull String toString() {
        return Arrays.toString(this.getValue());
    }

    @Override
    public final @NotNull LongArrayTag clone() {
        return new LongArrayTag(this.getValue().clone());
    }

    @Override
    public final void forEach(@NotNull Consumer<? super Long> action) {
        for (long l : this.getValue())
            action.accept(l);
    }

    @Override
    public final @NotNull Iterator<Long> iterator() {
        final long[] array = this.getValue();
        return new Iterator<>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return this.index < array.length;
            }

            @Override
            public Long next() {
                if (this.index >= array.length)
                    throw new NoSuchElementException();

                return array[this.index++];
            }
        };
    }

    @Override
    public final @NotNull Spliterator<Long> spliterator() {
        return Spliterators.spliterator(
            this.iterator(),
            this.length(),
            Spliterator.ORDERED | Spliterator.IMMUTABLE | Spliterator.SIZED
        );
    }

}
