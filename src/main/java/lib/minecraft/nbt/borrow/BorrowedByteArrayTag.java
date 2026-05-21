package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.io.util.NbtByteCodec;
import lib.minecraft.nbt.tags.array.ByteArrayTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Borrowed view over a {@link TapeKind#BYTE_ARRAY_PTR} tape entry. The tape element addresses a
 * 4-byte big-endian length prefix followed by {@code length} payload bytes.
 *
 * <p>{@link #rawList()} returns a zero-allocation {@link RawList} view over the payload; per-element
 * access through it does not copy. The inherited {@link #getValue()} allocates and copies the full
 * payload, deferred until first call.</p>
 *
 * @see ByteArrayTag
 */
@ApiStatus.Experimental
public final class BorrowedByteArrayTag extends ByteArrayTag {

    private final @NotNull Tape tape;

    private final int tapeIndex;

    BorrowedByteArrayTag(@NotNull Tape tape, int tapeIndex) {
        super(decoder(tape, tapeIndex));
        this.tape = tape;
        this.tapeIndex = tapeIndex;
    }

    private static @NotNull Supplier<byte[]> decoder(@NotNull Tape tape, int tapeIndex) {
        return () -> {
            int offset = (int) TapeElement.unpackValue(tape.elementAt(tapeIndex));
            int len = NbtByteCodec.getInt(tape.buffer(), offset);
            return new RawList(tape.buffer(), offset + 4, len, TapeKind.BYTE_ARRAY_PTR).toByteArray();
        };
    }

    /**
     * Number of bytes in the array (read from the 4-byte big-endian length prefix without
     * materializing the payload).
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
     * Iterates over every {@code byte} in the array in order, invoking {@code consumer} for each
     * element. Reads each value directly from the retained tape buffer - no {@code byte[]} is
     * allocated.
     *
     * <p>Reuses {@link ByteArrayTag.ByteConsumer} - the JDK does not ship a primitive
     * {@code ByteConsumer} variant.</p>
     *
     * @param consumer the action to perform on each element
     */
    public void forEachBorrowed(ByteArrayTag.@NotNull ByteConsumer consumer) {
        this.rawList().forEachByte(consumer);
    }

}
