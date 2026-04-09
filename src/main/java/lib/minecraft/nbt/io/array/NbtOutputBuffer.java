package dev.sbs.minecraftapi.nbt.io.array;

import dev.sbs.minecraftapi.nbt.io.NbtByteCodec;
import dev.sbs.minecraftapi.nbt.io.NbtModifiedUtf8;
import dev.sbs.minecraftapi.nbt.io.NbtOutput;
import org.jetbrains.annotations.NotNull;

import java.io.DataOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UTFDataFormatException;

/**
 * High-performance NBT serialization that writes directly to a byte buffer.
 *
 * <p>Byte-level primitive writes delegate to {@link NbtByteCodec} which uses {@code VarHandle}
 * intrinsics against the raw {@code byte[]}. String writes use {@link NbtModifiedUtf8} to match
 * the Mojang wire format byte-for-byte. The {@code writeListTag} and {@code writeCompoundTag}
 * implementations are inherited from {@link NbtOutput} as default methods - this class only
 * overrides what the raw byte-array backing can do faster than the default.</p>
 */
public class NbtOutputBuffer implements NbtOutput, DataOutput {

    /**
     * Default initial capacity (4 KB).
     *
     * <p>JMH profiling (post Round 1 + Round 2) showed the prior 32 KB default dominated the
     * serialization allocation budget at ~34 KB per op on a synthetic ~2 KB payload - 94% of the
     * allocation was the pre-sized buffer itself, not the NBT work. 4 KB covers a typical
     * SkyBlock item root compound including a modest ExtraAttributes subtree in a single
     * allocation, and the {@code ensureCapacity} doubling growth gracefully handles larger
     * enriched payloads (4 KB -&gt; 8 KB -&gt; 16 KB -&gt; 32 KB = three resizes copying ~28 KB
     * cumulative, identical to the prior waste but now only paid for when actually needed).</p>
     *
     * <p>Callers with known-larger payloads can still request an explicit capacity via
     * {@link #NbtOutputBuffer(int)}.</p>
     */
    private static final int DEFAULT_INITIAL_CAPACITY = 4 * 1024;

    private byte[] buffer;
    private int position;

    public NbtOutputBuffer() {
        this(DEFAULT_INITIAL_CAPACITY);
    }

    public NbtOutputBuffer(int initialCapacity) {
        this.buffer = new byte[initialCapacity];
        this.position = 0;
    }

    private void ensureCapacity(int additional) {
        int required = this.position + additional;

        if (required > this.buffer.length) {
            int newSize = Math.max(this.buffer.length * 2, required);
            byte[] newBuffer = new byte[newSize];
            System.arraycopy(this.buffer, 0, newBuffer, 0, this.position);
            this.buffer = newBuffer;
        }
    }

    // ------------------------------------------------------------------
    // Zero-copy accessors for NbtFactory
    // ------------------------------------------------------------------

    /**
     * Returns a trimmed copy of the bytes written so far. Allocates a new array.
     */
    public byte[] toByteArray() {
        byte[] result = new byte[this.position];
        System.arraycopy(this.buffer, 0, result, 0, this.position);
        return result;
    }

    /**
     * Returns the internal backing array without trimming. Caller must also read {@link #size()}
     * to know how many leading bytes are valid. Intended for zero-copy handoff to compression or
     * other consumers that accept a {@code (byte[], offset, length)} triple - skips the trimming
     * {@code arraycopy} that {@link #toByteArray()} performs.
     */
    public byte[] rawBuffer() {
        return this.buffer;
    }

    /**
     * Returns the number of bytes written so far. Used together with {@link #rawBuffer()}.
     */
    public int size() {
        return this.position;
    }

    /**
     * Writes the bytes accumulated so far to {@code outputStream} in a single call. Zero-copy -
     * no intermediate array is allocated. The buffer cursor is not reset; further writes append
     * after the already-flushed bytes.
     */
    public void writeTo(@NotNull OutputStream outputStream) throws IOException {
        outputStream.write(this.buffer, 0, this.position);
    }

    // ------------------------------------------------------------------
    // DataOutput primitives
    // ------------------------------------------------------------------

    @Override
    public void write(int b) {
        this.ensureCapacity(1);
        this.buffer[this.position++] = (byte) b;
    }

    @Override
    public void write(byte[] bytes) {
        this.write(bytes, 0, bytes.length);
    }

    @Override
    public void write(byte[] bytes, int offset, int length) {
        this.ensureCapacity(length);
        System.arraycopy(bytes, offset, this.buffer, this.position, length);
        this.position += length;
    }

    @Override
    public void writeBoolean(boolean value) {
        this.ensureCapacity(1);
        this.buffer[this.position++] = (byte) (value ? 1 : 0);
    }

    @Override
    public void writeByte(int value) {
        this.ensureCapacity(1);
        this.buffer[this.position++] = (byte) value;
    }

    @Override
    public void writeChar(int value) {
        throw new UnsupportedOperationException("writeChar() is not supported");
    }

    @Override
    public void writeShort(int value) {
        this.ensureCapacity(2);
        NbtByteCodec.putShort(this.buffer, this.position, value);
        this.position += 2;
    }

    @Override
    public void writeInt(int value) {
        this.ensureCapacity(4);
        NbtByteCodec.putInt(this.buffer, this.position, value);
        this.position += 4;
    }

    @Override
    public void writeLong(long value) {
        this.ensureCapacity(8);
        NbtByteCodec.putLong(this.buffer, this.position, value);
        this.position += 8;
    }

    @Override
    public void writeFloat(float value) {
        this.writeInt(Float.floatToIntBits(value));
    }

    @Override
    public void writeDouble(double value) {
        this.writeLong(Double.doubleToLongBits(value));
    }

    @Override
    public void writeBytes(String value) {
        throw new UnsupportedOperationException("writeBytes() is not supported");
    }

    @Override
    public void writeChars(String value) {
        throw new UnsupportedOperationException("writeChars() is not supported");
    }

    @Override
    public void writeUTF(@NotNull String value) throws IOException {
        int strLen = value.length();

        // Fast scan: any code unit outside [0x01..0x7F] triggers the modified UTF-8 slow path.
        // U+0000 is excluded because modified UTF-8 encodes it as two bytes (C0 80), not one.
        // All other ASCII code points encode the same way in modified UTF-8 and standard UTF-8,
        // so the fast path below emits bytes identical to the spec.
        for (int i = 0; i < strLen; i++) {
            char c = value.charAt(i);

            if (c == 0 || c >= 0x80) {
                this.writeModifiedUtf8Slow(value);
                return;
            }
        }

        // ASCII-without-NUL fast path: byte length equals char length, single sizing call.
        if (strLen > 65535)
            throw new UTFDataFormatException("UTF string too long: " + strLen);

        this.ensureCapacity(2 + strLen);
        byte[] buf = this.buffer;
        int p = this.position;

        buf[p++] = (byte) (strLen >>> 8);
        buf[p++] = (byte) strLen;

        for (int i = 0; i < strLen; i++)
            buf[p++] = (byte) value.charAt(i);

        this.position = p;
    }

    private void writeModifiedUtf8Slow(@NotNull String value) throws IOException {
        int byteLen = NbtModifiedUtf8.encodedLength(value);

        if (byteLen > 65535)
            throw new UTFDataFormatException("UTF string too long: " + byteLen);

        this.ensureCapacity(2 + byteLen);
        NbtByteCodec.putShort(this.buffer, this.position, byteLen);
        this.position += 2;
        this.position += NbtModifiedUtf8.encode(value, this.buffer, this.position);
    }

    // ------------------------------------------------------------------
    // NBT bulk primitive arrays (overrides for raw byte-array speed)
    // ------------------------------------------------------------------

    @Override
    public void writeByteArray(byte @NotNull [] value) {
        this.writeInt(value.length);
        this.write(value);
    }

    @Override
    public void writeIntArray(int @NotNull [] value) {
        int length = value.length;
        // Length prefix + 4 bytes per element, sized in one call.
        this.ensureCapacity(4 + (length << 2));

        byte[] buf = this.buffer;
        int p = this.position;

        NbtByteCodec.putInt(buf, p, length);
        p += 4;

        for (int i = 0; i < length; i++) {
            NbtByteCodec.putInt(buf, p, value[i]);
            p += 4;
        }

        this.position = p;
    }

    @Override
    public void writeLongArray(long @NotNull [] value) {
        int length = value.length;
        // Length prefix + 8 bytes per element, sized in one call.
        this.ensureCapacity(4 + (length << 3));

        byte[] buf = this.buffer;
        int p = this.position;

        NbtByteCodec.putInt(buf, p, length);
        p += 4;

        for (int i = 0; i < length; i++) {
            NbtByteCodec.putLong(buf, p, value[i]);
            p += 8;
        }

        this.position = p;
    }

}
