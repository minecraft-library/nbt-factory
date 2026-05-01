package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.tags.TagType;
import lib.minecraft.nbt.tags.primitive.ShortTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Borrowed view over a {@link TapeKind#SHORT_INLINE} tape entry. The short value is sign-extended
 * into the low 56 bits of the packed tape element.
 *
 * @see ShortTag
 */
@ApiStatus.Experimental
public final class BorrowedShortTag implements BorrowedTag<Short> {

    private final @NotNull Tape tape;

    private final int tapeIndex;

    BorrowedShortTag(@NotNull Tape tape, int tapeIndex) {
        this.tape = tape;
        this.tapeIndex = tapeIndex;
    }

    /**
     * Decodes the {@code short} value from the inline tape element.
     *
     * @return the short value
     */
    public short getShortValue() {
        return (short) TapeElement.unpackValue(this.tape.elementAt(this.tapeIndex));
    }

    @Override
    public byte getId() {
        return TagType.SHORT.getId();
    }

    @Override
    public @NotNull ShortTag materialize() {
        return new ShortTag(this.getShortValue());
    }

}
