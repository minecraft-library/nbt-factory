package dev.sbs.minecraftapi.nbt.io.stream;

import dev.sbs.minecraftapi.nbt.io.NbtByteCodec;
import dev.sbs.minecraftapi.nbt.io.NbtInput;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * NBT deserialization that reads from an input stream.
 *
 * <p>Wraps the provided stream in a {@link BufferedInputStream} unless it is already buffered, so
 * callers passing a raw {@code FileInputStream}, {@code GZIPInputStream}, or socket stream do not
 * pay per-byte syscall overhead through {@link DataInputStream}.</p>
 *
 * <p>Modified UTF-8 and the primitive byte-level reads are inherited from {@link DataInputStream}
 * unchanged. {@code readListTag} and {@code readCompoundTag} are inherited from {@link NbtInput}
 * as default methods - this class only overrides the bulk primitive array reads where a scratch
 * buffer plus {@link NbtByteCodec} is faster than per-element {@code readInt}/{@code readLong}
 * method calls through {@link DataInputStream}.</p>
 */
@SuppressWarnings("all")
public class NbtInputStream extends DataInputStream implements NbtInput {

    public NbtInputStream(@NotNull InputStream inputStream) throws IOException {
        super(inputStream instanceof BufferedInputStream ? inputStream : new BufferedInputStream(inputStream));
    }

    @Override
    public byte @NotNull [] readByteArray() throws IOException {
        byte[] data = new byte[this.readInt()];
        this.readFully(data);
        return data;
    }

    @Override
    public int @NotNull [] readIntArray() throws IOException {
        int length = this.readInt();
        int[] data = new int[length];

        // Bulk-read the raw bytes in one call (the underlying BufferedInputStream does a single
        // arraycopy), then decode in memory via NbtByteCodec. Eliminates N method-call chains
        // through DataInputStream.readInt.
        byte[] scratch = new byte[length << 2];
        this.readFully(scratch);

        int p = 0;
        for (int i = 0; i < length; i++) {
            data[i] = NbtByteCodec.getInt(scratch, p);
            p += 4;
        }

        return data;
    }

    @Override
    public long @NotNull [] readLongArray() throws IOException {
        int length = this.readInt();
        long[] data = new long[length];

        byte[] scratch = new byte[length << 3];
        this.readFully(scratch);

        int p = 0;
        for (int i = 0; i < length; i++) {
            data[i] = NbtByteCodec.getLong(scratch, p);
            p += 8;
        }

        return data;
    }

}
