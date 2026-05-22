package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.tags.primitive.ByteTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Borrowed view over a {@link TapeKind#BYTE_INLINE} tape entry. The byte value is sign-extended
 * into the low 56 bits of the packed tape element; {@link #byteValue()} decodes it back via
 * {@link TapeElement#unpackValue(long)} with no buffer touch and no boxed wrapper.
 *
 * @see ByteTag
 */
@ApiStatus.Experimental
public final class BorrowedByteTag extends ByteTag {

    private final @NotNull Tape tape;

    private final int tapeIndex;

    BorrowedByteTag(@NotNull Tape tape, int tapeIndex) {
        super(decoder(tape, tapeIndex));
        this.tape = tape;
        this.tapeIndex = tapeIndex;
    }

    private static @NotNull Supplier<Byte> decoder(@NotNull Tape tape, int tapeIndex) {
        return () -> (byte) TapeElement.unpackValue(tape.elementAt(tapeIndex));
    }

    @Override
    public byte byteValue() {
        return (byte) TapeElement.unpackValue(this.tape.elementAt(this.tapeIndex));
    }

}
