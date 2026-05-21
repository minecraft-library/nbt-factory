package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.tags.primitive.IntTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Borrowed view over a {@link TapeKind#INT_INLINE} tape entry. The int value is sign-extended
 * into the low 56 bits of the packed tape element.
 *
 * @see IntTag
 */
@ApiStatus.Experimental
public final class BorrowedIntTag extends IntTag {

    BorrowedIntTag(@NotNull Tape tape, int tapeIndex) {
        super(decoder(tape, tapeIndex));
    }

    private static @NotNull Supplier<Integer> decoder(@NotNull Tape tape, int tapeIndex) {
        return () -> (int) TapeElement.unpackValue(tape.elementAt(tapeIndex));
    }

}
