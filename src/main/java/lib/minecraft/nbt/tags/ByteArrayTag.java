package lib.minecraft.nbt.tags;

import lib.minecraft.nbt.tags.borrow.BorrowedByteArrayTag;
import lib.minecraft.nbt.tags.borrow.RawList;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

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

    /**
     * Constructs a byte array tag whose backing array is supplied lazily on first access. Used
     * by {@link BorrowedByteArrayTag}.
     */
    public ByteArrayTag(@NotNull Supplier<byte[]> supplier) {
        super(supplier);
    }

    @Override
    public final byte getId() {
        return TagType.BYTE_ARRAY.getId();
    }

    /**
     * Number of elements in this byte array tag.
     *
     * <p>Borrow subclasses override this to read the size from the tape header without
     * materializing the payload.</p>
     */
    public int length() {
        return this.getValue().length;
    }

    /**
     * Returns the byte at the specified position in this array tag.
     *
     * <p>Borrow subclasses override this to read the single byte directly from the retained
     * buffer, skipping the full-payload materialize.</p>
     *
     * @param index index of the element to return
     * @return the byte at the specified position
     */
    public byte get(int index) {
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
        if (!(o instanceof ByteArrayTag that)) return false;
        return Arrays.equals(this.getValue(), that.getValue());
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
    public void forEach(@NotNull Consumer<? super Byte> action) {
        for (byte b : this.getValue())
            action.accept(b);
    }

    /**
     * Performs the given action for each element of the backing {@code byte[]} without boxing.
     *
     * <p>The JDK does not ship a {@code ByteConsumer} primitive functional interface, so this tag
     * declares its own. Use this overload in preference to {@link #forEach(Consumer)} when the
     * action does not require a boxed {@link Byte}.</p>
     *
     * <p>Borrow subclasses override this to walk the tape's {@link RawList} directly, skipping
     * the full-payload materialize.</p>
     *
     * @param action the action to perform on each byte
     */
    public void forEachByte(@NotNull ByteConsumer action) {
        for (byte b : this.getValue())
            action.accept(b);
    }

    /**
     * Primitive {@code byte} consumer functional interface.
     *
     * <p>Mirrors {@link IntConsumer} / {@link LongConsumer}
     * which the JDK ships, filling the gap left by the absence of a primitive {@code byte}
     * variant.</p>
     */
    @FunctionalInterface
    public interface ByteConsumer {

        /**
         * Performs this operation on the given byte argument.
         *
         * @param value the input byte
         */
        void accept(byte value);

    }

    @Override
    public @NotNull Iterator<Byte> iterator() {
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
    public @NotNull Spliterator<Byte> spliterator() {
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
