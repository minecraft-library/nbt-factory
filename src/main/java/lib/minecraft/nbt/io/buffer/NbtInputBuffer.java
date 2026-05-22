package lib.minecraft.nbt.io.buffer;

import lib.minecraft.nbt.exception.NbtFormatException;
import lib.minecraft.nbt.io.NbtInput;
import lib.minecraft.nbt.io.stream.NbtInputStream;
import lib.minecraft.nbt.io.util.NbtByteCodec;
import lib.minecraft.nbt.io.util.NbtKnownKeys;
import lib.minecraft.nbt.io.util.NbtModifiedUtf8;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
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
 * @see NbtInputStream
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
    public void readFully(byte[] b) {
        this.readFully(b, 0, b.length);
    }

    @Override
    public void readFully(byte[] b, int off, int len) {
        if (len < 0)
            throw new IndexOutOfBoundsException();

        this.requireRemaining(len);
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
    public boolean readBoolean() {
        this.requireRemaining(1);
        return this.buffer[this.position++] != 0;
    }

    @Override
    public byte readByte() {
        this.requireRemaining(1);
        return this.buffer[this.position++];
    }

    @Override
    public int readUnsignedByte() {
        this.requireRemaining(1);
        return this.buffer[this.position++] & 0xFF;
    }

    @Override
    public short readShort() {
        this.requireRemaining(2);
        short v = NbtByteCodec.getShort(this.buffer, this.position);
        this.position += 2;
        return v;
    }

    @Override
    public int readUnsignedShort() {
        this.requireRemaining(2);
        int v = NbtByteCodec.getUnsignedShort(this.buffer, this.position);
        this.position += 2;
        return v;
    }

    @Override
    public char readChar() {
        throw new UnsupportedOperationException("readChar() is not supported");
    }

    @Override
    public int readInt() {
        this.requireRemaining(4);
        int v = NbtByteCodec.getInt(this.buffer, this.position);
        this.position += 4;
        return v;
    }

    @Override
    public long readLong() {
        this.requireRemaining(8);
        long v = NbtByteCodec.getLong(this.buffer, this.position);
        this.position += 8;
        return v;
    }

    @Override
    public float readFloat() {
        return Float.intBitsToFloat(this.readInt());
    }

    @Override
    public double readDouble() {
        return Double.longBitsToDouble(this.readLong());
    }

    @Override
    public String readLine() {
        throw new UnsupportedOperationException("readLine() is not supported");
    }

    @Override
    public @NotNull String readUTF() throws IOException {
        int utfLen = this.readUnsignedShort();
        this.requireRemaining(utfLen);

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
    public byte @NotNull [] readByteArray() {
        int length = this.readInt();
        byte[] data = new byte[length];
        this.readFully(data);
        return data;
    }

    @Override
    public int @NotNull [] readIntArray() {
        int length = this.readInt();
        // long arithmetic to avoid overflow on a pathological length.
        this.requireRemainingBytes((long) length << 2);

        int[] data = new int[length];
        NbtByteCodec.getIntArrayBE(this.buffer, this.position, data, 0, length);
        this.position += length << 2;
        return data;
    }

    @Override
    public long @NotNull [] readLongArray() {
        int length = this.readInt();
        this.requireRemainingBytes((long) length << 3);

        long[] data = new long[length];
        NbtByteCodec.getLongArrayBE(this.buffer, this.position, data, 0, length);
        this.position += length << 3;
        return data;
    }

    private void requireRemaining(int byteCount) {
        if (this.position + byteCount > this.buffer.length)
            throw new NbtFormatException(
                "Truncated NBT input - need %d bytes at offset %d, only %d available",
                byteCount, this.position, this.buffer.length - this.position);
    }

    /**
     * {@code long}-arithmetic overload for the bulk-array readers - {@code length << 2/3} can
     * overflow {@code int} on pathological array lengths.
     */
    private void requireRemainingBytes(long byteCount) {
        if (this.position + byteCount > this.buffer.length)
            throw new NbtFormatException(
                "Truncated NBT input - need %d bytes at offset %d, only %d available",
                byteCount, this.position, this.buffer.length - this.position);
    }

}
