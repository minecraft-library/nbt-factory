package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.io.util.NbtByteCodec;
import lib.minecraft.nbt.tags.array.IntArrayTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Borrowed view over a {@link TapeKind#INT_ARRAY_PTR} tape entry. The tape element addresses a
 * 4-byte big-endian length prefix followed by {@code length * 4} payload bytes (big-endian ints).
 *
 * <p>{@link #rawList()} returns a zero-allocation {@link RawList} view over the payload; the
 * inherited {@link #getValue()} allocates a fresh {@code int[]} on first call.</p>
 *
 * @see IntArrayTag
 */
@ApiStatus.Experimental
public final class BorrowedIntArrayTag extends IntArrayTag {

    private final @NotNull Tape tape;

    private final int tapeIndex;

    BorrowedIntArrayTag(@NotNull Tape tape, int tapeIndex) {
        super(decoder(tape, tapeIndex));
        this.tape = tape;
        this.tapeIndex = tapeIndex;
    }

    private static @NotNull Supplier<int[]> decoder(@NotNull Tape tape, int tapeIndex) {
        return () -> {
            int offset = (int) TapeElement.unpackValue(tape.elementAt(tapeIndex));
            int len = NbtByteCodec.getInt(tape.buffer(), offset);
            return new RawList(tape.buffer(), offset + 4, len, TapeKind.INT_ARRAY_PTR).toIntArray();
        };
    }

    /**
     * Number of ints in the array (read from the 4-byte big-endian length prefix without
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
        return new RawList(this.tape.buffer(), offset + 4, len, TapeKind.INT_ARRAY_PTR);
    }

    /**
     * Iterates over every {@code int} in the array in order, invoking {@code consumer} for each
     * element. Reads each value from the retained tape buffer via
     * {@link NbtByteCodec#getInt(byte[], int)} - no {@code int[]} is allocated.
     *
     * @param consumer the action to perform on each element
     */
    public void forEachBorrowed(@NotNull IntConsumer consumer) {
        this.rawList().forEachInt(consumer);
    }

}
