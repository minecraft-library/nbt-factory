package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.io.util.NbtByteCodec;
import lib.minecraft.nbt.tags.ByteArrayTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Borrowed view over a {@link TapeKind#BYTE_ARRAY_PTR} tape entry. The tape element addresses a
 * 4-byte big-endian length prefix followed by {@code length} payload bytes.
 *
 * <p>The standard {@link ByteArrayTag} accessor surface ({@link #length()}, {@link #get(int)},
 * {@link #forEachByte(ByteConsumer)}) is overridden here to read direct from the retained buffer
 * via the same {@link RawList} the borrow API has always exposed. The inherited
 * {@link #getValue()} still allocates and copies the full payload, deferred until first call.</p>
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
     * Number of bytes in the array. Reads the 4-byte big-endian length prefix from the retained
     * buffer without materializing the payload.
     */
    @Override
    public int length() {
        int offset = (int) TapeElement.unpackValue(this.tape.elementAt(this.tapeIndex));
        return NbtByteCodec.getInt(this.tape.buffer(), offset);
    }

    /**
     * Reads the byte at {@code index} directly from the retained buffer.
     */
    @Override
    public byte get(int index) {
        int offset = (int) TapeElement.unpackValue(this.tape.elementAt(this.tapeIndex));
        return this.tape.buffer()[offset + 4 + index];
    }

    @Override
    public void forEachByte(@NotNull ByteConsumer action) {
        this.rawList().forEachByte(action);
    }

    /**
     * Alias for {@link #length()} - retained from the pre-subclass borrow API.
     */
    public int size() {
        return this.length();
    }

    /**
     * Returns a zero-allocation {@link RawList} view over the payload bytes. Use this when
     * iterating without going through the standard {@link #forEachByte(ByteConsumer)} entry point
     * (e.g. for the array-kind discriminator on {@link RawList#elementKind()}).
     */
    public @NotNull RawList rawList() {
        int offset = (int) TapeElement.unpackValue(this.tape.elementAt(this.tapeIndex));
        int len = NbtByteCodec.getInt(this.tape.buffer(), offset);
        return new RawList(this.tape.buffer(), offset + 4, len, TapeKind.BYTE_ARRAY_PTR);
    }

}
