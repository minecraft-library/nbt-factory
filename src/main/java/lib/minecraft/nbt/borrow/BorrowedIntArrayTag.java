package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.io.NbtByteCodec;
import lib.minecraft.nbt.tags.TagType;
import lib.minecraft.nbt.tags.array.IntArrayTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Borrowed view over a {@link TapeKind#INT_ARRAY_PTR} tape entry. The tape element addresses a
 * 4-byte big-endian length prefix followed by {@code length * 4} payload bytes (big-endian ints).
 *
 * <p>{@link #rawList()} returns a zero-allocation {@link RawList} view over the payload;
 * {@link #toIntArray()} allocates a fresh {@code int[]} and bulk-byteswaps via
 * {@link NbtByteCodec#getIntArrayBE(byte[], int, int[], int, int)}.</p>
 *
 * @see IntArrayTag
 */
@ApiStatus.Experimental
public final class BorrowedIntArrayTag implements BorrowedTag<int[]> {

    private final @NotNull Tape tape;

    private final int tapeIndex;

    BorrowedIntArrayTag(@NotNull Tape tape, int tapeIndex) {
        this.tape = tape;
        this.tapeIndex = tapeIndex;
    }

    /**
     * Number of ints in the array (read from the 4-byte big-endian length prefix).
     *
     * @return the element count
     */
    public int size() {
        int offset = (int) TapeElement.unpackValue(this.tape.elementAt(this.tapeIndex));
        return NbtByteCodec.getInt(this.tape.buffer(), offset);
    }

    /**
     * Returns a zero-allocation {@link RawList} view over the payload bytes.
     *
     * @return the raw-list view
     */
    public @NotNull RawList rawList() {
        int offset = (int) TapeElement.unpackValue(this.tape.elementAt(this.tapeIndex));
        int len = NbtByteCodec.getInt(this.tape.buffer(), offset);
        return new RawList(this.tape.buffer(), offset + 4, len, TapeKind.INT_ARRAY_PTR);
    }

    /**
     * Allocates a fresh {@code int[]} and bulk-byteswaps every element into it.
     *
     * @return a freshly allocated copy of the payload
     */
    public int @NotNull [] toIntArray() {
        return this.rawList().toIntArray();
    }

    @Override
    public byte getId() {
        return TagType.INT_ARRAY.getId();
    }

    @Override
    public @NotNull IntArrayTag materialize() {
        return new IntArrayTag(this.toIntArray());
    }

}
