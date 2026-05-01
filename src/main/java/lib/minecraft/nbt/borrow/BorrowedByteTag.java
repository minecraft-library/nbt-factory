package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.tags.TagType;
import lib.minecraft.nbt.tags.primitive.ByteTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Borrowed view over a {@link TapeKind#BYTE_INLINE} tape entry. The byte value is sign-extended
 * into the low 56 bits of the packed tape element; reads decode it back via
 * {@link TapeElement#unpackValue(long)} with no buffer touch.
 *
 * @see ByteTag
 */
@ApiStatus.Experimental
public final class BorrowedByteTag implements BorrowedTag<Byte> {

    private final @NotNull Tape tape;

    private final int tapeIndex;

    BorrowedByteTag(@NotNull Tape tape, int tapeIndex) {
        this.tape = tape;
        this.tapeIndex = tapeIndex;
    }

    /**
     * Decodes the {@code byte} value from the inline tape element.
     *
     * @return the byte value
     */
    public byte getByteValue() {
        return (byte) TapeElement.unpackValue(this.tape.elementAt(this.tapeIndex));
    }

    @Override
    public byte getId() {
        return TagType.BYTE.getId();
    }

    @Override
    public @NotNull ByteTag materialize() {
        return ByteTag.of(this.getByteValue());
    }

}
