package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.io.NbtByteCodec;
import lib.minecraft.nbt.tags.TagType;
import lib.minecraft.nbt.tags.array.ByteArrayTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Borrowed view over a {@link TapeKind#BYTE_ARRAY_PTR} tape entry. The tape element addresses a
 * 4-byte big-endian length prefix followed by {@code length} payload bytes.
 *
 * <p>{@link #rawList()} returns a zero-allocation {@link RawList} view over the payload; per-element
 * access through it does not copy. {@link #toByteArray()} allocates and copies the full payload.</p>
 *
 * @see ByteArrayTag
 */
@ApiStatus.Experimental
public final class BorrowedByteArrayTag implements BorrowedTag<byte[]> {

    private final @NotNull Tape tape;

    private final int tapeIndex;

    BorrowedByteArrayTag(@NotNull Tape tape, int tapeIndex) {
        this.tape = tape;
        this.tapeIndex = tapeIndex;
    }

    /**
     * Number of bytes in the array (read from the 4-byte big-endian length prefix).
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
        return new RawList(this.tape.buffer(), offset + 4, len, TapeKind.BYTE_ARRAY_PTR);
    }

    /**
     * Allocates a fresh {@code byte[]} and copies every element into it.
     *
     * @return a freshly allocated copy of the payload
     */
    public byte @NotNull [] toByteArray() {
        return this.rawList().toByteArray();
    }

    /**
     * Iterates over every {@code byte} in the array in order, invoking {@code consumer} for each
     * element. Reads each value directly from the retained tape buffer - no {@code byte[]} is
     * allocated.
     *
     * <p>Reuses {@link ByteArrayTag.ByteConsumer} - the JDK does not ship a primitive
     * {@code ByteConsumer} variant.</p>
     *
     * @param consumer the action to perform on each element
     */
    public void forEachByte(ByteArrayTag.@NotNull ByteConsumer consumer) {
        this.rawList().forEachByte(consumer);
    }

    @Override
    public byte getId() {
        return TagType.BYTE_ARRAY.getId();
    }

    @Override
    public @NotNull ByteArrayTag materialize() {
        return new ByteArrayTag(this.toByteArray());
    }

}
