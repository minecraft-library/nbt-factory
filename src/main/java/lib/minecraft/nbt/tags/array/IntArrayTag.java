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
import java.util.stream.IntStream;

/**
 * {@link TagType#INT_ARRAY} (ID 11) is used for storing an ordered sequence of 32-bit signed integers.
 *
 * <p>Backed by a primitive {@code int[]} - no per-element boxing.</p>
 */
public class IntArrayTag extends Tag<int[]> implements Iterable<Integer> {

    private static final int[] EMPTY_ARRAY = new int[0];

    public static final @NotNull IntArrayTag EMPTY = new IntArrayTag(EMPTY_ARRAY) {
        @Override
        public void setValue(int @NotNull [] value) {
            throw new UnsupportedOperationException("This nbt tag is not modifiable.");
        }
    };

    /**
     * Constructs an empty int array tag.
     */
    public IntArrayTag() {
        super(EMPTY_ARRAY);
    }

    /**
     * Constructs an unnamed int array tag wrapping the given primitive {@code int[]}.
     *
     * @param value the tag's primitive {@code int[]} value
     */
    public IntArrayTag(int @NotNull ... value) {
        super(value);
    }

    @Override
    public final byte getId() {
        return TagType.INT_ARRAY.getId();
    }

    /**
     * Number of elements in this int array tag.
     */
    public final int length() {
        return this.getValue().length;
    }

    /**
     * Returns the int at the specified position in this array tag.
     *
     * @param index index of the element to return
     * @return the int at the specified position
     */
    public final int get(int index) {
        return this.getValue()[index];
    }

    /**
     * Replaces the element at the specified position with the given int.
     *
     * @param index index of the element to replace
     * @param element int to be stored at the specified position
     * @return the previous value at the specified position
     */
    public final int set(int index, int element) {
        int[] array = this.getValue();
        int previous = array[index];
        array[index] = element;
        return previous;
    }

    /**
     * Returns a sequential {@link IntStream} over the backing {@code int[]} - zero boxing.
     */
    public final @NotNull IntStream intStream() {
        return Arrays.stream(this.getValue());
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Arrays.equals(this.getValue(), ((IntArrayTag) o).getValue());
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
    public final @NotNull IntArrayTag clone() {
        return new IntArrayTag(this.getValue().clone());
    }

    @Override
    public final void forEach(@NotNull Consumer<? super Integer> action) {
        for (int i : this.getValue())
            action.accept(i);
    }

    @Override
    public final @NotNull Iterator<Integer> iterator() {
        final int[] array = this.getValue();
        return new Iterator<>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return this.index < array.length;
            }

            @Override
            public Integer next() {
                if (this.index >= array.length)
                    throw new NoSuchElementException();

                return array[this.index++];
            }
        };
    }

    @Override
    public final @NotNull Spliterator<Integer> spliterator() {
        return Spliterators.spliterator(
            this.iterator(),
            this.length(),
            Spliterator.ORDERED | Spliterator.IMMUTABLE | Spliterator.SIZED
        );
    }

}
