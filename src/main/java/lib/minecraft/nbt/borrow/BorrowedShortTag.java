package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.tags.primitive.ShortTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Borrowed view over a {@link TapeKind#SHORT_INLINE} tape entry. The short value is sign-extended
 * into the low 56 bits of the packed tape element.
 *
 * @see ShortTag
 */
@ApiStatus.Experimental
public final class BorrowedShortTag extends ShortTag {

    BorrowedShortTag(@NotNull Tape tape, int tapeIndex) {
        super(decoder(tape, tapeIndex));
    }

    private static @NotNull Supplier<Short> decoder(@NotNull Tape tape, int tapeIndex) {
        return () -> (short) TapeElement.unpackValue(tape.elementAt(tapeIndex));
    }

}
