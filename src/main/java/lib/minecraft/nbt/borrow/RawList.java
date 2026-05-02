package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.exception.NbtException;
import lib.minecraft.nbt.io.NbtByteCodec;
import lib.minecraft.nbt.tags.array.ByteArrayTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Spliterator;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.StreamSupport;

/**
 * Zero-copy view over a length-prefixed primitive array embedded in a borrowed NBT buffer.
 *
 * <p>Mirrors {@code simdnbt::raw_list::RawList} ({@code simdnbt/src/raw_list.rs:7-48}). Holds a
 * {@code (buffer, offset, count, elementKind)} triple where {@code offset} addresses the first
 * element byte (NOT the 4-byte length prefix that precedes it on the wire) and {@code elementKind}
 * is one of {@link TapeKind#BYTE_ARRAY_PTR}, {@link TapeKind#INT_ARRAY_PTR},
 * {@link TapeKind#LONG_ARRAY_PTR}.</p>
 *
 * <p>The view itself allocates nothing - per-element access via {@link #getByte(int)} /
 * {@link #getInt(int)} / {@link #getLong(int)} reads directly from the retained buffer. Bulk
 * conversion via {@link #toByteArray()} / {@link #toIntArray()} / {@link #toLongArray()} allocates
 * the destination array and byteswaps via {@link NbtByteCodec}'s
 * {@link java.lang.invoke.VarHandle}-driven big-endian primitives, matching the existing
 * materializing path's behavior.</p>
 *
 * <p>Used by C3's {@code BorrowedByteArrayTag} / {@code BorrowedIntArrayTag} /
 * {@code BorrowedLongArrayTag} navigators - the raw-list view is the wrapped-in field, and the
 * borrowed-tag types add the {@code Tag} interface around it. C2 only provides this type; nothing
 * in the parser itself constructs a {@code RawList} (the parser records buffer offsets in tape
 * elements, and C3 instantiates {@code RawList} from those offsets when the borrowed-tag type is
 * dereferenced).</p>
 */
@ApiStatus.Experimental
public final class RawList {

    private final byte @NotNull [] buffer;

    private final int offset;

    private final int count;

    private final @NotNull TapeKind elementKind;

    /**
     * Constructs a raw-list view.
     *
     * @param buffer the retained NBT buffer; not mutated by any method on this view
     * @param offset byte offset of the first element (NOT the 4-byte length prefix)
     * @param count number of elements in the list
     * @param elementKind one of {@link TapeKind#BYTE_ARRAY_PTR}, {@link TapeKind#INT_ARRAY_PTR},
     *     {@link TapeKind#LONG_ARRAY_PTR}
     * @throws NbtException if {@code elementKind} is not an array kind, or if {@code offset} /
     *     {@code count} is negative, or if the addressed range falls outside {@code buffer}
     */
    public RawList(byte @NotNull [] buffer, int offset, int count, @NotNull TapeKind elementKind) {
        int elementSize = elementSizeFor(elementKind);

        if (offset < 0)
            throw new NbtException("RawList offset must be non-negative: %d", offset);

        if (count < 0)
            throw new NbtException("RawList count must be non-negative: %d", count);

        long requiredEnd = (long) offset + (long) count * elementSize;

        if (requiredEnd > buffer.length)
            throw new NbtException(
                "RawList range [%d, %d) overflows buffer length %d",
                offset, requiredEnd, buffer.length
            );

        this.buffer = buffer;
        this.offset = offset;
        this.count = count;
        this.elementKind = elementKind;
    }

    /**
     * Number of elements in this raw list.
     *
     * @return the element count
     */
    public int size() {
        return this.count;
    }

    /**
     * Element kind discriminant - {@link TapeKind#BYTE_ARRAY_PTR}, {@link TapeKind#INT_ARRAY_PTR},
     * or {@link TapeKind#LONG_ARRAY_PTR}.
     *
     * @return the element kind
     */
    public @NotNull TapeKind elementKind() {
        return this.elementKind;
    }

    /**
     * Reads the byte at index {@code i}. Valid only when {@link #elementKind()} is
     * {@link TapeKind#BYTE_ARRAY_PTR}.
     *
     * @param i element index, {@code 0..size()-1}
     * @return the byte value
     * @throws NbtException if the element kind is not {@link TapeKind#BYTE_ARRAY_PTR}
     * @throws IndexOutOfBoundsException if {@code i} is out of range
     */
    public byte getByte(int i) {
        if (this.elementKind != TapeKind.BYTE_ARRAY_PTR)
            throw new NbtException("RawList.getByte called on %s view", this.elementKind);

        if (i < 0 || i >= this.count)
            throw new IndexOutOfBoundsException("index out of range: " + i);

        return this.buffer[this.offset + i];
    }

    /**
     * Reads the int at index {@code i}. Valid only when {@link #elementKind()} is
     * {@link TapeKind#INT_ARRAY_PTR}.
     *
     * @param i element index, {@code 0..size()-1}
     * @return the int value (decoded as big-endian)
     * @throws NbtException if the element kind is not {@link TapeKind#INT_ARRAY_PTR}
     * @throws IndexOutOfBoundsException if {@code i} is out of range
     */
    public int getInt(int i) {
        if (this.elementKind != TapeKind.INT_ARRAY_PTR)
            throw new NbtException("RawList.getInt called on %s view", this.elementKind);

        if (i < 0 || i >= this.count)
            throw new IndexOutOfBoundsException("index out of range: " + i);

        return NbtByteCodec.getInt(this.buffer, this.offset + (i << 2));
    }

    /**
     * Reads the long at index {@code i}. Valid only when {@link #elementKind()} is
     * {@link TapeKind#LONG_ARRAY_PTR}.
     *
     * @param i element index, {@code 0..size()-1}
     * @return the long value (decoded as big-endian)
     * @throws NbtException if the element kind is not {@link TapeKind#LONG_ARRAY_PTR}
     * @throws IndexOutOfBoundsException if {@code i} is out of range
     */
    public long getLong(int i) {
        if (this.elementKind != TapeKind.LONG_ARRAY_PTR)
            throw new NbtException("RawList.getLong called on %s view", this.elementKind);

        if (i < 0 || i >= this.count)
            throw new IndexOutOfBoundsException("index out of range: " + i);

        return NbtByteCodec.getLong(this.buffer, this.offset + (i << 3));
    }

    /**
     * Allocates a fresh {@code byte[]} and copies every element into it. Valid only when
     * {@link #elementKind()} is {@link TapeKind#BYTE_ARRAY_PTR}.
     *
     * @return a fresh array of length {@link #size()}
     * @throws NbtException if the element kind is not {@link TapeKind#BYTE_ARRAY_PTR}
     */
    public byte @NotNull [] toByteArray() {
        if (this.elementKind != TapeKind.BYTE_ARRAY_PTR)
            throw new NbtException("RawList.toByteArray called on %s view", this.elementKind);

        byte[] dst = new byte[this.count];
        System.arraycopy(this.buffer, this.offset, dst, 0, this.count);
        return dst;
    }

    /**
     * Allocates a fresh {@code int[]} and decodes every big-endian element into it via
     * {@link NbtByteCodec#getIntArrayBE(byte[], int, int[], int, int)}. Valid only when
     * {@link #elementKind()} is {@link TapeKind#INT_ARRAY_PTR}.
     *
     * @return a fresh array of length {@link #size()}
     * @throws NbtException if the element kind is not {@link TapeKind#INT_ARRAY_PTR}
     */
    public int @NotNull [] toIntArray() {
        if (this.elementKind != TapeKind.INT_ARRAY_PTR)
            throw new NbtException("RawList.toIntArray called on %s view", this.elementKind);

        int[] dst = new int[this.count];
        NbtByteCodec.getIntArrayBE(this.buffer, this.offset, dst, 0, this.count);
        return dst;
    }

    /**
     * Allocates a fresh {@code long[]} and decodes every big-endian element into it via
     * {@link NbtByteCodec#getLongArrayBE(byte[], int, long[], int, int)}. Valid only when
     * {@link #elementKind()} is {@link TapeKind#LONG_ARRAY_PTR}.
     *
     * @return a fresh array of length {@link #size()}
     * @throws NbtException if the element kind is not {@link TapeKind#LONG_ARRAY_PTR}
     */
    public long @NotNull [] toLongArray() {
        if (this.elementKind != TapeKind.LONG_ARRAY_PTR)
            throw new NbtException("RawList.toLongArray called on %s view", this.elementKind);

        long[] dst = new long[this.count];
        NbtByteCodec.getLongArrayBE(this.buffer, this.offset, dst, 0, this.count);
        return dst;
    }

    /**
     * Lazy {@link IntStream} that decodes each element on demand from the retained buffer via
     * {@link NbtByteCodec#getInt(byte[], int)}. No {@code int[]} is allocated; downstream operations
     * such as {@code intStream().sum()} or {@code intStream().filter(...).count()} run in
     * {@code O(N)} without the materialization overhead that {@link #toIntArray()} pays.
     *
     * <p>Valid only when {@link #elementKind()} is {@link TapeKind#INT_ARRAY_PTR}. The stream is
     * bound to the lifetime of the underlying buffer; the spliterator captures the {@code byte[]}
     * directly so the buffer stays reachable as long as the stream pipeline retains the
     * spliterator.</p>
     *
     * @return a lazy {@link IntStream} over this list's elements
     * @throws NbtException if the element kind is not {@link TapeKind#INT_ARRAY_PTR}
     */
    public @NotNull IntStream intStream() {
        if (this.elementKind != TapeKind.INT_ARRAY_PTR)
            throw new NbtException("RawList.intStream called on %s view", this.elementKind);

        return StreamSupport.intStream(this.spliteratorOfInt(), false);
    }

    /**
     * Lazy {@link LongStream} that decodes each element on demand from the retained buffer via
     * {@link NbtByteCodec#getLong(byte[], int)}.
     *
     * <p>Valid only when {@link #elementKind()} is {@link TapeKind#LONG_ARRAY_PTR}.</p>
     *
     * @return a lazy {@link LongStream} over this list's elements
     * @throws NbtException if the element kind is not {@link TapeKind#LONG_ARRAY_PTR}
     */
    public @NotNull LongStream longStream() {
        if (this.elementKind != TapeKind.LONG_ARRAY_PTR)
            throw new NbtException("RawList.longStream called on %s view", this.elementKind);

        return StreamSupport.longStream(this.spliteratorOfLong(), false);
    }

    /**
     * Returns a {@link Spliterator.OfInt} that walks this list's elements without allocating an
     * intermediate {@code int[]}. The spliterator supports {@link Spliterator#trySplit()} on a
     * 4-byte boundary so parallel streams can bisect the underlying byte range.
     *
     * <p>Valid only when {@link #elementKind()} is {@link TapeKind#INT_ARRAY_PTR}.</p>
     *
     * @return an int-specialized spliterator over this list's elements
     * @throws NbtException if the element kind is not {@link TapeKind#INT_ARRAY_PTR}
     */
    public Spliterator.@NotNull OfInt spliteratorOfInt() {
        if (this.elementKind != TapeKind.INT_ARRAY_PTR)
            throw new NbtException("RawList.spliteratorOfInt called on %s view", this.elementKind);

        return new IntArraySpliterator(this.buffer, this.offset, this.offset + (this.count << 2));
    }

    /**
     * Returns a {@link Spliterator.OfLong} that walks this list's elements without allocating an
     * intermediate {@code long[]}. The spliterator supports {@link Spliterator#trySplit()} on an
     * 8-byte boundary so parallel streams can bisect the underlying byte range.
     *
     * <p>Valid only when {@link #elementKind()} is {@link TapeKind#LONG_ARRAY_PTR}.</p>
     *
     * @return a long-specialized spliterator over this list's elements
     * @throws NbtException if the element kind is not {@link TapeKind#LONG_ARRAY_PTR}
     */
    public Spliterator.@NotNull OfLong spliteratorOfLong() {
        if (this.elementKind != TapeKind.LONG_ARRAY_PTR)
            throw new NbtException("RawList.spliteratorOfLong called on %s view", this.elementKind);

        return new LongArraySpliterator(this.buffer, this.offset, this.offset + (this.count << 3));
    }

    /**
     * Iterates over every {@code int} in the array in order, invoking {@code consumer} for each
     * element. Reads each element from the retained buffer via
     * {@link NbtByteCodec#getInt(byte[], int)} - no {@code int[]} is allocated.
     *
     * <p>Valid only when {@link #elementKind()} is {@link TapeKind#INT_ARRAY_PTR}.</p>
     *
     * @param consumer the action to perform on each element
     * @throws NbtException if the element kind is not {@link TapeKind#INT_ARRAY_PTR}
     */
    public void forEachInt(@NotNull IntConsumer consumer) {
        if (this.elementKind != TapeKind.INT_ARRAY_PTR)
            throw new NbtException("RawList.forEachInt called on %s view", this.elementKind);

        int end = this.offset + (this.count << 2);
        for (int p = this.offset; p < end; p += 4)
            consumer.accept(NbtByteCodec.getInt(this.buffer, p));
    }

    /**
     * Iterates over every {@code long} in the array in order, invoking {@code consumer} for each
     * element. Reads each element from the retained buffer via
     * {@link NbtByteCodec#getLong(byte[], int)} - no {@code long[]} is allocated.
     *
     * <p>Valid only when {@link #elementKind()} is {@link TapeKind#LONG_ARRAY_PTR}.</p>
     *
     * @param consumer the action to perform on each element
     * @throws NbtException if the element kind is not {@link TapeKind#LONG_ARRAY_PTR}
     */
    public void forEachLong(@NotNull LongConsumer consumer) {
        if (this.elementKind != TapeKind.LONG_ARRAY_PTR)
            throw new NbtException("RawList.forEachLong called on %s view", this.elementKind);

        int end = this.offset + (this.count << 3);
        for (int p = this.offset; p < end; p += 8)
            consumer.accept(NbtByteCodec.getLong(this.buffer, p));
    }

    /**
     * Iterates over every {@code byte} in the array in order, invoking {@code consumer} for each
     * element. Reuses {@link ByteArrayTag.ByteConsumer} - the JDK does not ship a primitive
     * {@code ByteConsumer} variant.
     *
     * <p>Valid only when {@link #elementKind()} is {@link TapeKind#BYTE_ARRAY_PTR}.</p>
     *
     * @param consumer the action to perform on each element
     * @throws NbtException if the element kind is not {@link TapeKind#BYTE_ARRAY_PTR}
     */
    public void forEachByte(ByteArrayTag.@NotNull ByteConsumer consumer) {
        if (this.elementKind != TapeKind.BYTE_ARRAY_PTR)
            throw new NbtException("RawList.forEachByte called on %s view", this.elementKind);

        int end = this.offset + this.count;
        for (int p = this.offset; p < end; p++)
            consumer.accept(this.buffer[p]);
    }

    private static int elementSizeFor(@NotNull TapeKind kind) {
        return switch (kind) {
            case BYTE_ARRAY_PTR -> 1;
            case INT_ARRAY_PTR -> 4;
            case LONG_ARRAY_PTR -> 8;
            default -> throw new NbtException("RawList element kind must be a *_ARRAY_PTR, got %s", kind);
        };
    }

    /**
     * Spliterator over a contiguous big-endian {@code int} run inside a retained byte buffer.
     *
     * <p>Reads each element via {@link NbtByteCodec#getInt(byte[], int)} (a single
     * {@link java.lang.invoke.VarHandle} access; the JIT typically intrinsifies this to
     * {@code MOVBE}). {@link #trySplit()} bisects the remaining range on a 4-byte boundary;
     * sub-spliterators access disjoint byte ranges so parallel reduction is safe without
     * synchronization.</p>
     */
    private static final class IntArraySpliterator implements Spliterator.OfInt {

        private static final int CHARACTERISTICS =
            Spliterator.SIZED | Spliterator.SUBSIZED |
            Spliterator.ORDERED | Spliterator.NONNULL | Spliterator.IMMUTABLE;

        private final byte @NotNull [] buffer;

        private int position;

        private final int end;

        IntArraySpliterator(byte @NotNull [] buffer, int position, int end) {
            this.buffer = buffer;
            this.position = position;
            this.end = end;
        }

        @Override
        public boolean tryAdvance(@NotNull IntConsumer action) {
            if (this.position >= this.end)
                return false;

            action.accept(NbtByteCodec.getInt(this.buffer, this.position));
            this.position += 4;
            return true;
        }

        @Override
        public void forEachRemaining(@NotNull IntConsumer action) {
            int p = this.position;
            int e = this.end;
            byte[] buf = this.buffer;
            while (p < e) {
                action.accept(NbtByteCodec.getInt(buf, p));
                p += 4;
            }
            this.position = e;
        }

        @Override
        public Spliterator.@NotNull OfInt trySplit() {
            int remaining = this.end - this.position;
            // 8 ints (32 bytes) is the smallest split worth taking.
            if (remaining < 64)
                return null;

            int half = (remaining >>> 3) << 2; // half the element count, in bytes (4-byte aligned)
            int splitEnd = this.position + half;
            IntArraySpliterator prefix = new IntArraySpliterator(this.buffer, this.position, splitEnd);
            this.position = splitEnd;
            return prefix;
        }

        @Override
        public long estimateSize() {
            return (long) (this.end - this.position) >>> 2;
        }

        @Override
        public int characteristics() {
            return CHARACTERISTICS;
        }

    }

    /**
     * Spliterator over a contiguous big-endian {@code long} run inside a retained byte buffer.
     *
     * <p>Mirrors {@link IntArraySpliterator} - 8-byte stride, reads via
     * {@link NbtByteCodec#getLong(byte[], int)}, splits on an 8-byte boundary.</p>
     */
    private static final class LongArraySpliterator implements Spliterator.OfLong {

        private static final int CHARACTERISTICS =
            Spliterator.SIZED | Spliterator.SUBSIZED |
            Spliterator.ORDERED | Spliterator.NONNULL | Spliterator.IMMUTABLE;

        private final byte @NotNull [] buffer;

        private int position;

        private final int end;

        LongArraySpliterator(byte @NotNull [] buffer, int position, int end) {
            this.buffer = buffer;
            this.position = position;
            this.end = end;
        }

        @Override
        public boolean tryAdvance(@NotNull LongConsumer action) {
            if (this.position >= this.end)
                return false;

            action.accept(NbtByteCodec.getLong(this.buffer, this.position));
            this.position += 8;
            return true;
        }

        @Override
        public void forEachRemaining(@NotNull LongConsumer action) {
            int p = this.position;
            int e = this.end;
            byte[] buf = this.buffer;
            while (p < e) {
                action.accept(NbtByteCodec.getLong(buf, p));
                p += 8;
            }
            this.position = e;
        }

        @Override
        public Spliterator.@NotNull OfLong trySplit() {
            int remaining = this.end - this.position;
            // 8 longs (64 bytes) is the smallest split worth taking.
            if (remaining < 128)
                return null;

            int half = (remaining >>> 4) << 3; // half the element count, in bytes (8-byte aligned)
            int splitEnd = this.position + half;
            LongArraySpliterator prefix = new LongArraySpliterator(this.buffer, this.position, splitEnd);
            this.position = splitEnd;
            return prefix;
        }

        @Override
        public long estimateSize() {
            return (long) (this.end - this.position) >>> 3;
        }

        @Override
        public int characteristics() {
            return CHARACTERISTICS;
        }

    }

}
