package lib.minecraft.nbt.tags.borrow;

import lib.minecraft.nbt.tags.ShortTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Borrowed view over a {@link TapeKind#SHORT_INLINE} tape entry. The short value is sign-extended
 * into the low 56 bits of the packed tape element; {@link #shortValue()} decodes it back with no
 * buffer touch and no boxed wrapper.
 *
 * @see ShortTag
 */
@ApiStatus.Experimental
public final class BorrowedShortTag extends ShortTag {

    private final @NotNull Tape tape;

    private final int tapeIndex;

    BorrowedShortTag(@NotNull Tape tape, int tapeIndex) {
        super(decoder(tape, tapeIndex));
        this.tape = tape;
        this.tapeIndex = tapeIndex;
    }

    private static @NotNull Supplier<Short> decoder(@NotNull Tape tape, int tapeIndex) {
        return () -> (short) TapeElement.unpackValue(tape.elementAt(tapeIndex));
    }

    @Override
    public short shortValue() {
        return (short) TapeElement.unpackValue(this.tape.elementAt(this.tapeIndex));
    }

}
