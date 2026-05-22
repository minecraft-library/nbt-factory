package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.io.util.NbtByteCodec;
import lib.minecraft.nbt.tags.IntArrayTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * Borrowed view over a {@link TapeKind#INT_ARRAY_PTR} tape entry. The tape element addresses a
 * 4-byte big-endian length prefix followed by {@code length * 4} payload bytes (big-endian ints).
 *
 * <p>The standard {@link IntArrayTag} accessor surface ({@link #length()}, {@link #get(int)},
 * {@link #forEachInt(IntConsumer)}, {@link #intStream()}) is overridden here to read direct from
 * the retained buffer via the same {@link RawList} the borrow API has always exposed. The
 * inherited {@link #getValue()} still allocates and copies the full payload, deferred until first
 * call.</p>
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
     * Number of ints in the array. Reads the 4-byte big-endian length prefix from the retained
     * buffer without materializing the payload.
     */
    @Override
    public int length() {
        int offset = (int) TapeElement.unpackValue(this.tape.elementAt(this.tapeIndex));
        return NbtByteCodec.getInt(this.tape.buffer(), offset);
    }

    /**
     * Reads the int at {@code index} directly from the retained buffer via
     * {@link NbtByteCodec#getInt(byte[], int)}.
     */
    @Override
    public int get(int index) {
        int offset = (int) TapeElement.unpackValue(this.tape.elementAt(this.tapeIndex));
        return NbtByteCodec.getInt(this.tape.buffer(), offset + 4 + index * 4);
    }

    @Override
    public void forEachInt(@NotNull IntConsumer action) {
        this.rawList().forEachInt(action);
    }

    @Override
    public @NotNull IntStream intStream() {
        RawList list = this.rawList();
        return IntStream.range(0, list.size()).map(list::getInt);
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
        return new RawList(this.tape.buffer(), offset + 4, len, TapeKind.INT_ARRAY_PTR);
    }

}
