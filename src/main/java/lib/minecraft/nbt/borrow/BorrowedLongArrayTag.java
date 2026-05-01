package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.io.NbtByteCodec;
import lib.minecraft.nbt.tags.TagType;
import lib.minecraft.nbt.tags.array.LongArrayTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Borrowed view over a {@link TapeKind#LONG_ARRAY_PTR} tape entry. The tape element addresses a
 * 4-byte big-endian length prefix followed by {@code length * 8} payload bytes (big-endian longs).
 *
 * <p>{@link #rawList()} returns a zero-allocation {@link RawList} view over the payload;
 * {@link #toLongArray()} allocates a fresh {@code long[]} and bulk-byteswaps via
 * {@link NbtByteCodec#getLongArrayBE(byte[], int, long[], int, int)}.</p>
 *
 * @see LongArrayTag
 */
@ApiStatus.Experimental
public final class BorrowedLongArrayTag implements BorrowedTag<long[]> {

    private final @NotNull Tape tape;

    private final int tapeIndex;

    BorrowedLongArrayTag(@NotNull Tape tape, int tapeIndex) {
        this.tape = tape;
        this.tapeIndex = tapeIndex;
    }

    /**
     * Number of longs in the array (read from the 4-byte big-endian length prefix).
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
        return new RawList(this.tape.buffer(), offset + 4, len, TapeKind.LONG_ARRAY_PTR);
    }

    /**
     * Allocates a fresh {@code long[]} and bulk-byteswaps every element into it.
     *
     * @return a freshly allocated copy of the payload
     */
    public long @NotNull [] toLongArray() {
        return this.rawList().toLongArray();
    }

    @Override
    public byte getId() {
        return TagType.LONG_ARRAY.getId();
    }

    @Override
    public @NotNull LongArrayTag materialize() {
        return new LongArrayTag(this.toLongArray());
    }

}
