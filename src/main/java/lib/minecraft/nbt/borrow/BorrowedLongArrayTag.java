package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.io.util.NbtByteCodec;
import lib.minecraft.nbt.tags.array.LongArrayTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.LongConsumer;
import java.util.function.Supplier;
import java.util.stream.LongStream;

/**
 * Borrowed view over a {@link TapeKind#LONG_ARRAY_PTR} tape entry. The tape element addresses a
 * 4-byte big-endian length prefix followed by {@code length * 8} payload bytes (big-endian longs).
 *
 * <p>{@link #rawList()} returns a zero-allocation {@link RawList} view over the payload; the
 * inherited {@link #getValue()} allocates a fresh {@code long[]} on first call.</p>
 *
 * @see LongArrayTag
 */
@ApiStatus.Experimental
public final class BorrowedLongArrayTag extends LongArrayTag {

    private final @NotNull Tape tape;

    private final int tapeIndex;

    BorrowedLongArrayTag(@NotNull Tape tape, int tapeIndex) {
        super(decoder(tape, tapeIndex));
        this.tape = tape;
        this.tapeIndex = tapeIndex;
    }

    private static @NotNull Supplier<long[]> decoder(@NotNull Tape tape, int tapeIndex) {
        return () -> {
            int offset = (int) TapeElement.unpackValue(tape.elementAt(tapeIndex));
            int len = NbtByteCodec.getInt(tape.buffer(), offset);
            return new RawList(tape.buffer(), offset + 4, len, TapeKind.LONG_ARRAY_PTR).toLongArray();
        };
    }

    /**
     * Number of longs in the array (read from the 4-byte big-endian length prefix without
     * materializing the payload).
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
     * Returns a lazy {@link LongStream} over this array's elements that decodes each value on
     * demand from the retained tape buffer via {@link NbtByteCodec#getLong(byte[], int)}.
     *
     * <p>Recommended over the inherited {@link #getValue()} for {@code sum} / {@code filter} /
     * {@code reduce} pipelines that do not need the full array on heap - the {@code long[]}
     * allocation and the second pass over the materialized array are both elided.</p>
     *
     * <p>The stream is bound to the lifetime of the underlying tape buffer; if the borrow's buffer
     * is collected before the stream is consumed, behavior is undefined.</p>
     *
     * @return a lazy {@link LongStream} over the array's elements
     */
    public @NotNull LongStream borrowedLongStream() {
        return this.rawList().longStream();
    }

    /**
     * Iterates over every {@code long} in the array in order, invoking {@code consumer} for each
     * element. Reads each value from the retained tape buffer via
     * {@link NbtByteCodec#getLong(byte[], int)} - no {@code long[]} is allocated.
     *
     * @param consumer the action to perform on each element
     */
    public void forEachBorrowed(@NotNull LongConsumer consumer) {
        this.rawList().forEachLong(consumer);
    }

}
