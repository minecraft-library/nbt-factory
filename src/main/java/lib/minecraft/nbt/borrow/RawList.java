package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.exception.NbtException;
import lib.minecraft.nbt.io.NbtByteCodec;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

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

    private static int elementSizeFor(@NotNull TapeKind kind) {
        return switch (kind) {
            case BYTE_ARRAY_PTR -> 1;
            case INT_ARRAY_PTR -> 4;
            case LONG_ARRAY_PTR -> 8;
            default -> throw new NbtException("RawList element kind must be a *_ARRAY_PTR, got %s", kind);
        };
    }

}
