package lib.minecraft.nbt.tags.borrow;

import lib.minecraft.nbt.tags.FloatTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Borrowed view over a {@link TapeKind#FLOAT_INLINE} tape entry. The IEEE-754 32-bit bit pattern
 * is stashed in the low 56 bits and rehydrated via {@link Float#intBitsToFloat(int)};
 * {@link #floatValue()} decodes it back with no buffer touch and no {@link Float} wrapper
 * allocated ({@code Float} has no box cache, so the override is the only way to avoid the
 * per-tag allocation).
 *
 * @see FloatTag
 */
@ApiStatus.Experimental
public final class BorrowedFloatTag extends FloatTag {

    private final @NotNull Tape tape;

    private final int tapeIndex;

    BorrowedFloatTag(@NotNull Tape tape, int tapeIndex) {
        super(decoder(tape, tapeIndex));
        this.tape = tape;
        this.tapeIndex = tapeIndex;
    }

    private static @NotNull Supplier<Float> decoder(@NotNull Tape tape, int tapeIndex) {
        return () -> Float.intBitsToFloat((int) TapeElement.unpackValue(tape.elementAt(tapeIndex)));
    }

    @Override
    public float floatValue() {
        return Float.intBitsToFloat((int) TapeElement.unpackValue(this.tape.elementAt(this.tapeIndex)));
    }

}
