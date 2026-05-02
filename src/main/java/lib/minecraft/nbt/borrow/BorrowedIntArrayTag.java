package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.io.NbtByteCodec;
import lib.minecraft.nbt.tags.TagType;
import lib.minecraft.nbt.tags.array.IntArrayTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntConsumer;
import java.util.stream.IntStream;

/**
 * Borrowed view over a {@link TapeKind#INT_ARRAY_PTR} tape entry. The tape element addresses a
 * 4-byte big-endian length prefix followed by {@code length * 4} payload bytes (big-endian ints).
 *
 * <p>{@link #rawList()} returns a zero-allocation {@link RawList} view over the payload;
 * {@link #toIntArray()} allocates a fresh {@code int[]} and bulk-byteswaps via
 * {@link NbtByteCodec#getIntArrayBE(byte[], int, int[], int, int)}.</p>
 *
 * @see IntArrayTag
 */
@ApiStatus.Experimental
public final class BorrowedIntArrayTag implements BorrowedTag<int[]> {

    private final @NotNull Tape tape;

    private final int tapeIndex;

    BorrowedIntArrayTag(@NotNull Tape tape, int tapeIndex) {
        this.tape = tape;
        this.tapeIndex = tapeIndex;
    }

    /**
     * Number of ints in the array (read from the 4-byte big-endian length prefix).
     *
     * @return the element count
     */
    public int size() {
        int offset = (int) TapeElement.unpackValue(this.tape.elementAt(this.tapeIndex));
        return NbtByteCodec.getInt(this.tape.buffer(), offset);
    }

    /**
     * Returns a zero-allocation {@link RawList} view over the payload bytes.
     *
     * @return the raw-list view
     */
    public @NotNull RawList rawList() {
        int offset = (int) TapeElement.unpackValue(this.tape.elementAt(this.tapeIndex));
        int len = NbtByteCodec.getInt(this.tape.buffer(), offset);
        return new RawList(this.tape.buffer(), offset + 4, len, TapeKind.INT_ARRAY_PTR);
    }

    /**
     * Allocates a fresh {@code int[]} and bulk-byteswaps every element into it.
     *
     * @return a freshly allocated copy of the payload
     */
    public int @NotNull [] toIntArray() {
        return this.rawList().toIntArray();
    }

    /**
     * Returns a lazy {@link IntStream} over this array's elements that decodes each value on demand
     * from the retained tape buffer via {@link NbtByteCodec#getInt(byte[], int)}.
     *
     * <p>Recommended over {@link #toIntArray()} for {@code sum} / {@code filter} / {@code reduce}
     * pipelines that do not need the full array on heap - the {@code int[]} allocation and the
     * second pass over the materialized array are both elided.</p>
     *
     * <p>The stream is bound to the lifetime of the underlying tape buffer; if the borrow's buffer
     * is collected before the stream is consumed, behavior is undefined (the same retention
     * contract applies as elsewhere in the borrow API).</p>
     *
     * @return a lazy {@link IntStream} over the array's elements
     */
    public @NotNull IntStream intStream() {
        return this.rawList().intStream();
    }

    /**
     * Iterates over every {@code int} in the array in order, invoking {@code consumer} for each
     * element. Reads each value from the retained tape buffer via
     * {@link NbtByteCodec#getInt(byte[], int)} - no {@code int[]} is allocated.
     *
     * @param consumer the action to perform on each element
     */
    public void forEachInt(@NotNull IntConsumer consumer) {
        this.rawList().forEachInt(consumer);
    }

    @Override
    public byte getId() {
        return TagType.INT_ARRAY.getId();
    }

    @Override
    public @NotNull IntArrayTag materialize() {
        return new IntArrayTag(this.toIntArray());
    }

}
