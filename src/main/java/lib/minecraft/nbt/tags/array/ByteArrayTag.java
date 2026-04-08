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

/**
 * {@link TagType#BYTE_ARRAY} (ID 7) is used for storing an ordered sequence of 8-bit signed integers.
 *
 * <p>Backed by a primitive {@code byte[]} - no per-element boxing.</p>
 */
public class ByteArrayTag extends Tag<byte[]> implements Iterable<Byte> {

    private static final byte[] EMPTY_ARRAY = new byte[0];

    public static final @NotNull ByteArrayTag EMPTY = new ByteArrayTag(EMPTY_ARRAY) {
        @Override
        public void setValue(byte @NotNull [] value) {
            throw new UnsupportedOperationException("This nbt tag is not modifiable.");
        }
    };

    /**
     * Constructs an empty byte array tag.
     */
    public ByteArrayTag() {
        super(EMPTY_ARRAY);
    }

    /**
     * Constructs an unnamed byte array tag wrapping the given primitive {@code byte[]}.
     *
     * @param value the tag's primitive {@code byte[]} value
     */
    public ByteArrayTag(byte @NotNull ... value) {
        super(value);
    }

    @Override
    public final byte getId() {
        return TagType.BYTE_ARRAY.getId();
    }

    /**
     * Number of elements in this byte array tag.
     */
    public final int length() {
        return this.getValue().length;
    }

    /**
     * Returns the byte at the specified position in this array tag.
     *
     * @param index index of the element to return
     * @return the byte at the specified position
     */
    public final byte get(int index) {
        return this.getValue()[index];
    }

    /**
     * Replaces the element at the specified position with the given byte.
     *
     * @param index index of the element to replace
     * @param element byte to be stored at the specified position
     * @return the previous value at the specified position
     */
    public final byte set(int index, byte element) {
        byte[] array = this.getValue();
        byte previous = array[index];
        array[index] = element;
        return previous;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Arrays.equals(this.getValue(), ((ByteArrayTag) o).getValue());
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
    public final @NotNull ByteArrayTag clone() {
        return new ByteArrayTag(this.getValue().clone());
    }

    @Override
    public final void forEach(@NotNull Consumer<? super Byte> action) {
        for (byte b : this.getValue())
            action.accept(b);
    }

    @Override
    public final @NotNull Iterator<Byte> iterator() {
        final byte[] array = this.getValue();
        return new Iterator<>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return this.index < array.length;
            }

            @Override
            public Byte next() {
                if (this.index >= array.length)
                    throw new NoSuchElementException();

                return array[this.index++];
            }
        };
    }

    @Override
    public final @NotNull Spliterator<Byte> spliterator() {
        final byte[] array = this.getValue();
        return Spliterators.spliterator(
            new Iterator<Byte>() {
                private int index = 0;

                @Override
                public boolean hasNext() {
                    return this.index < array.length;
                }

                @Override
                public Byte next() {
                    if (this.index >= array.length)
                        throw new NoSuchElementException();

                    return array[this.index++];
                }
            },
            array.length,
            Spliterator.ORDERED | Spliterator.IMMUTABLE | Spliterator.SIZED
        );
    }

}
