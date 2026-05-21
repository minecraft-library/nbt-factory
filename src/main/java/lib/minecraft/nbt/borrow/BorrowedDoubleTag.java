package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.io.util.NbtByteCodec;
import lib.minecraft.nbt.tags.primitive.DoubleTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Borrowed view over a {@link TapeKind#DOUBLE_PTR} tape entry. 64-bit values do not fit in the
 * 56-bit inline payload, so the tape stores the buffer offset and the supplied initializer reads
 * the 8 big-endian bytes via {@link NbtByteCodec#getDouble(byte[], int)} on the first
 * {@link #getValue()} call.
 *
 * @see DoubleTag
 */
@ApiStatus.Experimental
public final class BorrowedDoubleTag extends DoubleTag {

    BorrowedDoubleTag(@NotNull Tape tape, int tapeIndex) {
        super(decoder(tape, tapeIndex));
    }

    private static @NotNull Supplier<Double> decoder(@NotNull Tape tape, int tapeIndex) {
        return () -> {
            int offset = (int) TapeElement.unpackValue(tape.elementAt(tapeIndex));
            return NbtByteCodec.getDouble(tape.buffer(), offset);
        };
    }

}
