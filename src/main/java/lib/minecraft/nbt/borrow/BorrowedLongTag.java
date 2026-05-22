package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.io.util.NbtByteCodec;
import lib.minecraft.nbt.tags.primitive.LongTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Borrowed view over a {@link TapeKind#LONG_PTR} tape entry. 64-bit values do not fit in the
 * 56-bit inline payload, so the tape stores the buffer offset and {@link #longValue()} reads the
 * 8 big-endian bytes via {@link NbtByteCodec#getLong(byte[], int)} on demand - no boxed wrapper
 * allocated for the unbox path.
 *
 * @see LongTag
 */
@ApiStatus.Experimental
public final class BorrowedLongTag extends LongTag {

    private final @NotNull Tape tape;

    private final int tapeIndex;

    BorrowedLongTag(@NotNull Tape tape, int tapeIndex) {
        super(decoder(tape, tapeIndex));
        this.tape = tape;
        this.tapeIndex = tapeIndex;
    }

    private static @NotNull Supplier<Long> decoder(@NotNull Tape tape, int tapeIndex) {
        return () -> {
            int offset = (int) TapeElement.unpackValue(tape.elementAt(tapeIndex));
            return NbtByteCodec.getLong(tape.buffer(), offset);
        };
    }

    @Override
    public long longValue() {
        int offset = (int) TapeElement.unpackValue(this.tape.elementAt(this.tapeIndex));
        return NbtByteCodec.getLong(this.tape.buffer(), offset);
    }

}
