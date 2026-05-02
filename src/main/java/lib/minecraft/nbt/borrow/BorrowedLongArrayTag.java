package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.io.NbtByteCodec;
import lib.minecraft.nbt.tags.TagType;
import lib.minecraft.nbt.tags.array.LongArrayTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.LongConsumer;
import java.util.stream.LongStream;

/**
 * Borrowed view over a {@link TapeKind#LONG_ARRAY_PTR} tape entry. The tape element addresses a
 * 4-byte big-endian length prefix followed by {@code length * 8} payload bytes (big-endian longs).
 *
 * <p>{@link #rawList()} returns a zero-allocation {@link RawList} view over the payload;
 * {@link #toLongArray()} allocates a fresh {@code long[]} and bulk-byteswaps via
 * {@link NbtByteCodec#getLongArrayBE(byte[], int, long[], int, int)}.</p>
 *
 * @see LongArrayTag
 */
@ApiStatus.Experimental
public final class BorrowedLongArrayTag implements BorrowedTag<long[]> {

    private final @NotNull Tape tape;

    private final int tapeIndex;

    BorrowedLongArrayTag(@NotNull Tape tape, int tapeIndex) {
        this.tape = tape;
        this.tapeIndex = tapeIndex;
    }

    /**
     * Number of longs in the array (read from the 4-byte big-endian length prefix).
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
        return new RawList(this.tape.buffer(), offset + 4, len, TapeKind.LONG_ARRAY_PTR);
    }

    /**
     * Allocates a fresh {@code long[]} and bulk-byteswaps every element into it.
     *
     * @return a freshly allocated copy of the payload
     */
    public long @NotNull [] toLongArray() {
        return this.rawList().toLongArray();
    }

    /**
     * Returns a lazy {@link LongStream} over this array's elements that decodes each value on
     * demand from the retained tape buffer via {@link NbtByteCodec#getLong(byte[], int)}.
     *
     * <p>Recommended over {@link #toLongArray()} for {@code sum} / {@code filter} / {@code reduce}
     * pipelines that do not need the full array on heap - the {@code long[]} allocation and the
     * second pass over the materialized array are both elided.</p>
     *
     * <p>The stream is bound to the lifetime of the underlying tape buffer; if the borrow's buffer
     * is collected before the stream is consumed, behavior is undefined.</p>
     *
     * @return a lazy {@link LongStream} over the array's elements
     */
    public @NotNull LongStream longStream() {
        return this.rawList().longStream();
    }

    /**
     * Iterates over every {@code long} in the array in order, invoking {@code consumer} for each
     * element. Reads each value from the retained tape buffer via
     * {@link NbtByteCodec#getLong(byte[], int)} - no {@code long[]} is allocated.
     *
     * @param consumer the action to perform on each element
     */
    public void forEachLong(@NotNull LongConsumer consumer) {
        this.rawList().forEachLong(consumer);
    }

    @Override
    public byte getId() {
        return TagType.LONG_ARRAY.getId();
    }

    @Override
    public @NotNull LongArrayTag materialize() {
        return new LongArrayTag(this.toLongArray());
    }

}
