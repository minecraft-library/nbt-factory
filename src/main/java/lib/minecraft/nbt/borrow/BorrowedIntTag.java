package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.tags.TagType;
import lib.minecraft.nbt.tags.primitive.IntTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Borrowed view over a {@link TapeKind#INT_INLINE} tape entry. The int value is sign-extended
 * into the low 56 bits of the packed tape element.
 *
 * @see IntTag
 */
@ApiStatus.Experimental
public final class BorrowedIntTag implements BorrowedTag<Integer> {

    private final @NotNull Tape tape;

    private final int tapeIndex;

    BorrowedIntTag(@NotNull Tape tape, int tapeIndex) {
        this.tape = tape;
        this.tapeIndex = tapeIndex;
    }

    /**
     * Decodes the {@code int} value from the inline tape element.
     *
     * @return the int value
     */
    public int getIntValue() {
        return (int) TapeElement.unpackValue(this.tape.elementAt(this.tapeIndex));
    }

    @Override
    public byte getId() {
        return TagType.INT.getId();
    }

    @Override
    public @NotNull IntTag materialize() {
        return IntTag.of(this.getIntValue());
    }

}
