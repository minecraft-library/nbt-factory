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
 * <p>The standard {@link LongArrayTag} accessor surface ({@link #length()}, {@link #get(int)},
 * {@link #forEachLong(LongConsumer)}, {@link #longStream()}) is overridden here to read direct
 * from the retained buffer via the same {@link RawList} the borrow API has always exposed. The
 * inherited {@link #getValue()} still allocates and copies the full payload, deferred until first
 * call.</p>
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
     * Number of longs in the array. Reads the 4-byte big-endian length prefix from the retained
     * buffer without materializing the payload.
     */
    @Override
    public int length() {
        int offset = (int) TapeElement.unpackValue(this.tape.elementAt(this.tapeIndex));
        return NbtByteCodec.getInt(this.tape.buffer(), offset);
    }

    /**
     * Reads the long at {@code index} directly from the retained buffer via
     * {@link NbtByteCodec#getLong(byte[], int)}.
     */
    @Override
    public long get(int index) {
        int offset = (int) TapeElement.unpackValue(this.tape.elementAt(this.tapeIndex));
        return NbtByteCodec.getLong(this.tape.buffer(), offset + 4 + index * 8);
    }

    @Override
    public void forEachLong(@NotNull LongConsumer action) {
        this.rawList().forEachLong(action);
    }

    @Override
    public @NotNull LongStream longStream() {
        return this.rawList().longStream();
    }

    /**
     * Alias for {@link #length()} - retained from the pre-subclass borrow API.
     */
    public int size() {
        return this.length();
    }

    /**
     * Returns a zero-allocation {@link RawList} view over the payload bytes.
     */
    public @NotNull RawList rawList() {
        int offset = (int) TapeElement.unpackValue(this.tape.elementAt(this.tapeIndex));
        int len = NbtByteCodec.getInt(this.tape.buffer(), offset);
        return new RawList(this.tape.buffer(), offset + 4, len, TapeKind.LONG_ARRAY_PTR);
    }

}
