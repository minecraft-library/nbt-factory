package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.io.NbtByteCodec;
import lib.minecraft.nbt.tags.TagType;
import lib.minecraft.nbt.tags.primitive.LongTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Borrowed view over a {@link TapeKind#LONG_PTR} tape entry. 64-bit values do not fit in the
 * 56-bit inline payload, so the tape stores the buffer offset and {@link #getLongValue()} reads
 * the 8 big-endian bytes via {@link NbtByteCodec#getLong(byte[], int)} on demand.
 *
 * @see LongTag
 */
@ApiStatus.Experimental
public final class BorrowedLongTag implements BorrowedTag<Long> {

    private final @NotNull Tape tape;

    private final int tapeIndex;

    BorrowedLongTag(@NotNull Tape tape, int tapeIndex) {
        this.tape = tape;
        this.tapeIndex = tapeIndex;
    }

    /**
     * Decodes the {@code long} value from the retained buffer at the offset addressed by the tape
     * element.
     *
     * @return the long value
     */
    public long getLongValue() {
        int offset = (int) TapeElement.unpackValue(this.tape.elementAt(this.tapeIndex));
        return NbtByteCodec.getLong(this.tape.buffer(), offset);
    }

    @Override
    public byte getId() {
        return TagType.LONG.getId();
    }

    @Override
    public @NotNull LongTag materialize() {
        return LongTag.of(this.getLongValue());
    }

}
