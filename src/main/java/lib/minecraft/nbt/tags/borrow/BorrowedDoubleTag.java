package lib.minecraft.nbt.tags.borrow;

import lib.minecraft.nbt.io.util.NbtByteCodec;
import lib.minecraft.nbt.tags.DoubleTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Borrowed view over a {@link TapeKind#DOUBLE_PTR} tape entry. 64-bit values do not fit in the
 * 56-bit inline payload, so the tape stores the buffer offset and {@link #doubleValue()} reads
 * the 8 big-endian bytes via {@link NbtByteCodec#getDouble(byte[], int)} on demand - no
 * {@link Double} wrapper allocated for the unbox path ({@code Double} has no box cache, so the
 * override is the only way to avoid the per-tag allocation).
 *
 * @see DoubleTag
 */
@ApiStatus.Experimental
public final class BorrowedDoubleTag extends DoubleTag {

    private final @NotNull Tape tape;

    private final int tapeIndex;

    BorrowedDoubleTag(@NotNull Tape tape, int tapeIndex) {
        super(decoder(tape, tapeIndex));
        this.tape = tape;
        this.tapeIndex = tapeIndex;
    }

    private static @NotNull Supplier<Double> decoder(@NotNull Tape tape, int tapeIndex) {
        return () -> {
            int offset = (int) TapeElement.unpackValue(tape.elementAt(tapeIndex));
            return NbtByteCodec.getDouble(tape.buffer(), offset);
        };
    }

    @Override
    public double doubleValue() {
        int offset = (int) TapeElement.unpackValue(this.tape.elementAt(this.tapeIndex));
        return NbtByteCodec.getDouble(this.tape.buffer(), offset);
    }

}
