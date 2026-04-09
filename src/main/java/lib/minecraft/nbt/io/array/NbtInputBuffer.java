package dev.sbs.minecraftapi.nbt.io.array;

import dev.sbs.minecraftapi.nbt.io.NbtByteCodec;
import dev.sbs.minecraftapi.nbt.io.NbtInput;
import dev.sbs.minecraftapi.nbt.io.NbtKnownKeys;
import dev.sbs.minecraftapi.nbt.io.NbtModifiedUtf8;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.EOFException;
import java.io.IOException;

/**
 * High-performance NBT deserialization that reads directly from a {@code byte[]} buffer,
 * decoding Minecraft's canonical big-endian binary wire format.
 *
 * <p>Implements Java Edition's binary NBT layout exactly as documented on the
 * <a href="https://minecraft.wiki/w/NBT_format">Minecraft Wiki NBT format</a> page:</p>
 * <ul>
 *   <li>All integer primitives are big-endian ({@code TAG_Short} 2 bytes,
 *       {@code TAG_Int}/{@code TAG_Float} 4 bytes, {@code TAG_Long}/{@code TAG_Double} 8 bytes).</li>
 *   <li>Strings ({@code TAG_String}, compound keys) are length-prefixed by a 2-byte big-endian
 *       unsigned short followed by {@code length} bytes of modified UTF-8 - decoded through
 *       {@link NbtModifiedUtf8} so {@code U+0000} and supplementary code points round-trip
 *       correctly.</li>
 *   <li>{@code TAG_Byte_Array} / {@code TAG_Int_Array} / {@code TAG_Long_Array} are each a
 *       4-byte big-endian signed length followed by {@code length} native-sized payloads.</li>
 *   <li>{@code TAG_List} and {@code TAG_Compound} framing is inherited from the
 *       {@link NbtInput} default implementations unchanged.</li>
 * </ul>
 *
 * <p>Byte-level primitive reads delegate to {@link NbtByteCodec} which uses {@code VarHandle}
 * intrinsics against the raw {@code byte[]} to compile down to a single big-endian load
 * instruction. Compound keys are further fast-pathed through
 * {@link NbtKnownKeys#match(byte[], int, int)} so a hit on the canonical NBT / SkyBlock key
 * vocabulary returns a shared interned {@code String} with zero allocation. The bulk primitive
 * array reads ({@code readByteArray}, {@code readIntArray}, {@code readLongArray}) are
 * overridden to perform a single up-front bounds check then decode all elements in a tight
 * {@code VarHandle} loop, skipping the per-element method-call chain the
 * {@link NbtInput} defaults would take.</p>
 *
 * <p>Implements {@link DataInput} as well as {@link NbtInput} so callers that already hold a
 * {@code DataInput}-shaped interface can consume this directly. {@code readChar()} and
 * {@code readLine()} are unsupported because neither appears in the NBT wire format.</p>
 *
 * @see NbtInput
 * @see dev.sbs.minecraftapi.nbt.io.stream.NbtInputStream
 * @see <a href="https://minecraft.wiki/w/NBT_format">Minecraft Wiki - NBT format</a>
 */
public class NbtInputBuffer implements NbtInput, DataInput {

    private final byte[] buffer;
    private int position;

    public NbtInputBuffer(byte[] buffer) {
        this.buffer = buffer;
        this.position = 0;
    }

    // ------------------------------------------------------------------
    // DataInput primitives
    // ------------------------------------------------------------------

    @Override
    public void readFully(byte[] b) throws IOException {
        this.readFully(b, 0, b.length);
    }

    @Override
    public void readFully(byte[] b, int off, int len) throws IOException {
        if (len < 0)
            throw new IndexOutOfBoundsException();

        if (this.position + len > this.buffer.length)
            throw new EOFException();

        System.arraycopy(this.buffer, this.position, b, off, len);
        this.position += len;
    }

    @Override
    public int skipBytes(int n) {
        int skip = Math.min(n, this.buffer.length - this.position);
        this.position += skip;
        return skip;
    }

    @Override
    public boolean readBoolean() throws IOException {
        if (this.position >= this.buffer.length)
            throw new EOFException();

        return this.buffer[this.position++] != 0;
    }

    @Override
    public byte readByte() throws IOException {
        if (this.position >= this.buffer.length)
            throw new EOFException();

        return this.buffer[this.position++];
    }

    @Override
    public int readUnsignedByte() throws IOException {
        if (this.position >= this.buffer.length)
            throw new EOFException();

        return this.buffer[this.position++] & 0xFF;
    }

    @Override
    public short readShort() throws IOException {
        if (this.position + 2 > this.buffer.length)
            throw new EOFException();

        short v = NbtByteCodec.getShort(this.buffer, this.position);
        this.position += 2;
        return v;
    }

    @Override
    public int readUnsignedShort() throws IOException {
        if (this.position + 2 > this.buffer.length)
            throw new EOFException();

        int v = NbtByteCodec.getUnsignedShort(this.buffer, this.position);
        this.position += 2;
        return v;
    }

    @Override
    public char readChar() {
        throw new UnsupportedOperationException("readChar() is not supported");
    }

    @Override
    public int readInt() throws IOException {
        if (this.position + 4 > this.buffer.length)
            throw new EOFException();

        int v = NbtByteCodec.getInt(this.buffer, this.position);
        this.position += 4;
        return v;
    }

    @Override
    public long readLong() throws IOException {
        if (this.position + 8 > this.buffer.length)
            throw new EOFException();

        long v = NbtByteCodec.getLong(this.buffer, this.position);
        this.position += 8;
        return v;
    }

    @Override
    public float readFloat() throws IOException {
        return Float.intBitsToFloat(this.readInt());
    }

    @Override
    public double readDouble() throws IOException {
        return Double.longBitsToDouble(this.readLong());
    }

    @Override
    public String readLine() {
        throw new UnsupportedOperationException("readLine() is not supported");
    }

    @Override
    public @NotNull String readUTF() throws IOException {
        int utfLen = this.readUnsignedShort();

        if (this.position + utfLen > this.buffer.length)
            throw new EOFException();

        // Well-known key match: returns a shared canonical String for common NBT keys without
        // allocating a new one. High hit rate on repeated compound-key reads (SkyBlock auction).
        String known = NbtKnownKeys.match(this.buffer, this.position, utfLen);

        if (known != null) {
            this.position += utfLen;
            return known;
        }

        String result = NbtModifiedUtf8.decode(this.buffer, this.position, utfLen);
        this.position += utfLen;
        return result;
    }

    // ------------------------------------------------------------------
    // NBT bulk primitive arrays (overrides for raw byte-array speed)
    // ------------------------------------------------------------------

    @Override
    public byte @NotNull [] readByteArray() throws IOException {
        int length = this.readInt();
        byte[] data = new byte[length];
        this.readFully(data);
        return data;
    }

    @Override
    public int @NotNull [] readIntArray() throws IOException {
        int length = this.readInt();

        // Single upfront bounds check using long arithmetic to avoid overflow on a pathological length.
        if (this.position + ((long) length << 2) > this.buffer.length)
            throw new EOFException();

        int[] data = new int[length];
        byte[] buf = this.buffer;
        int p = this.position;

        for (int i = 0; i < length; i++) {
            data[i] = NbtByteCodec.getInt(buf, p);
            p += 4;
        }

        this.position = p;
        return data;
    }

    @Override
    public long @NotNull [] readLongArray() throws IOException {
        int length = this.readInt();

        // Single upfront bounds check using long arithmetic to avoid overflow on a pathological length.
        if (this.position + ((long) length << 3) > this.buffer.length)
            throw new EOFException();

        long[] data = new long[length];
        byte[] buf = this.buffer;
        int p = this.position;

        for (int i = 0; i < length; i++) {
            data[i] = NbtByteCodec.getLong(buf, p);
            p += 8;
        }

        this.position = p;
        return data;
    }

}
