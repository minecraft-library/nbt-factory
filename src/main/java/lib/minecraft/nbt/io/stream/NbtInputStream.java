package dev.sbs.minecraftapi.nbt.io.stream;

import dev.sbs.minecraftapi.nbt.io.NbtByteCodec;
import dev.sbs.minecraftapi.nbt.io.NbtInput;
import dev.sbs.minecraftapi.nbt.io.buffer.NbtInputBuffer;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * NBT deserialization that reads Minecraft's canonical big-endian binary wire format from an
 * arbitrary {@link InputStream} - suitable for files, network sockets, and GZIP-wrapped payloads
 * alike.
 *
 * <p>Decodes Java Edition's binary NBT layout exactly as documented on the
 * <a href="https://minecraft.wiki/w/NBT_format">Minecraft Wiki NBT format</a> page. Primitive
 * byte-level reads and the modified-UTF-8 string decoder are inherited unchanged from
 * {@link DataInputStream}, which natively speaks big-endian and consumes the 2-byte length
 * prefix + modified-UTF-8 framing that NBT uses for every string. {@code readListTag} and
 * {@code readCompoundTag} are inherited from the {@link NbtInput} defaults, which encode the
 * {@code (type, name, value)} + {@code TAG_End} compound framing and the
 * {@code element-type + big-endian length} list framing.</p>
 *
 * <p>The constructor wraps the provided stream in a {@link BufferedInputStream} unless it is
 * already buffered, so callers passing a raw {@code FileInputStream}, {@code GZIPInputStream},
 * or socket stream do not pay per-byte syscall overhead through {@code DataInputStream}. The
 * bulk primitive array reads ({@code readByteArray}, {@code readIntArray}, {@code readLongArray})
 * are overridden to pull all element bytes in one {@code readFully} call then decode them
 * in-memory through {@link NbtByteCodec}, eliminating the N method-call chain the
 * {@code DataInputStream.readInt}/{@code readLong} defaults would take for big arrays.</p>
 *
 * <p>Implements {@link NbtInput} on top of {@link DataInputStream} rather than wrapping it so
 * callers can use this directly in either role.</p>
 *
 * @see NbtInput
 * @see NbtInputBuffer
 * @see <a href="https://minecraft.wiki/w/NBT_format">Minecraft Wiki - NBT format</a>
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
