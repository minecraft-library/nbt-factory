package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.tags.primitive.FloatTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Borrowed view over a {@link TapeKind#FLOAT_INLINE} tape entry. The IEEE-754 32-bit bit pattern
 * is stashed in the low 56 bits and rehydrated via {@link Float#intBitsToFloat(int)} on the first
 * {@link #getValue()} call.
 *
 * @see FloatTag
 */
@ApiStatus.Experimental
public final class BorrowedFloatTag extends FloatTag {

    BorrowedFloatTag(@NotNull Tape tape, int tapeIndex) {
        super(decoder(tape, tapeIndex));
    }

    private static @NotNull Supplier<Float> decoder(@NotNull Tape tape, int tapeIndex) {
        return () -> Float.intBitsToFloat((int) TapeElement.unpackValue(tape.elementAt(tapeIndex)));
    }

}
