package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.io.NbtByteCodec;
import lib.minecraft.nbt.tags.TagType;
import lib.minecraft.nbt.tags.primitive.DoubleTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Borrowed view over a {@link TapeKind#DOUBLE_PTR} tape entry. 64-bit values do not fit in the
 * 56-bit inline payload, so the tape stores the buffer offset and {@link #getDoubleValue()} reads
 * the 8 big-endian bytes via {@link NbtByteCodec#getDouble(byte[], int)} on demand.
 *
 * @see DoubleTag
 */
@ApiStatus.Experimental
public final class BorrowedDoubleTag implements BorrowedTag<Double> {

    private final @NotNull Tape tape;

    private final int tapeIndex;

    BorrowedDoubleTag(@NotNull Tape tape, int tapeIndex) {
        this.tape = tape;
        this.tapeIndex = tapeIndex;
    }

    /**
     * Decodes the {@code double} value from the retained buffer at the offset addressed by the
     * tape element.
     *
     * @return the double value
     */
    public double getDoubleValue() {
        int offset = (int) TapeElement.unpackValue(this.tape.elementAt(this.tapeIndex));
        return NbtByteCodec.getDouble(this.tape.buffer(), offset);
    }

    @Override
    public byte getId() {
        return TagType.DOUBLE.getId();
    }

    @Override
    public @NotNull DoubleTag materialize() {
        return new DoubleTag(this.getDoubleValue());
    }

}
