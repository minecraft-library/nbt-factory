package dev.sbs.minecraftapi.nbt.io.array;

import dev.sbs.minecraftapi.nbt.exception.NbtMaxDepthException;
import dev.sbs.minecraftapi.nbt.io.NbtInput;
import dev.sbs.minecraftapi.nbt.tags.Tag;
import dev.sbs.minecraftapi.nbt.tags.collection.CompoundTag;
import dev.sbs.minecraftapi.nbt.tags.collection.ListTag;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * High-performance NBT deserialization that reads directly from a byte buffer.
 */
public class NbtInputBuffer implements NbtInput, DataInput {

    private final byte[] buffer;
    private int position;

    public NbtInputBuffer(byte[] buffer) {
        this.buffer = buffer;
        this.position = 0;
    }

    // Optimized DataInput implementation
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
        
        return (short) (((this.buffer[this.position++] & 0xFF) << 8) |
                        (this.buffer[this.position++] & 0xFF));
    }

    @Override
    public int readUnsignedShort() throws IOException {
        if (this.position + 2 > this.buffer.length)
            throw new EOFException();
        
        return ((this.buffer[this.position++] & 0xFF) << 8) |
               (this.buffer[this.position++] & 0xFF);
    }

    @Override
    public char readChar() {
        throw new UnsupportedOperationException("readChar() is not supported");
    }

    @Override
    public int readInt() throws IOException {
        if (this.position + 4 > this.buffer.length)
            throw new EOFException();
        
        return ((this.buffer[this.position++] & 0xFF) << 24) |
               ((this.buffer[this.position++] & 0xFF) << 16) |
               ((this.buffer[this.position++] & 0xFF) << 8) |
               (this.buffer[this.position++] & 0xFF);
    }

    @Override
    public long readLong() throws IOException {
        if (this.position + 8 > this.buffer.length)
            throw new EOFException();
        
        return ((long)(this.buffer[this.position++] & 0xFF) << 56) |
               ((long)(this.buffer[this.position++] & 0xFF) << 48) |
               ((long)(this.buffer[this.position++] & 0xFF) << 40) |
               ((long)(this.buffer[this.position++] & 0xFF) << 32) |
               ((long)(this.buffer[this.position++] & 0xFF) << 24) |
               ((long)(this.buffer[this.position++] & 0xFF) << 16) |
               ((long)(this.buffer[this.position++] & 0xFF) << 8) |
               ((long)(this.buffer[this.position++] & 0xFF));
    }

    @Override
    public float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }

    @Override
    public double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
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

        // String(byte[], UTF_8) has its own ASCII fast path; pre-scanning was redundant.
        String result = new String(this.buffer, this.position, utfLen, StandardCharsets.UTF_8);
        this.position += utfLen;
        return result;
    }
    
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
        int[] data = new int[length];

        for (int i = 0; i < length; i++)
            data[i] = this.readInt();

        return data;
    }

    @Override
    public long @NotNull [] readLongArray() throws IOException {
        int length = this.readInt();
        long[] data = new long[length];

        for (int i = 0; i < length; i++)
            data[i] = this.readLong();

        return data;
    }

    @Override
    public @NotNull ListTag<?> readListTag(int depth) throws IOException {
        if (++depth >= 512)
            throw new NbtMaxDepthException();

        int listType = readUnsignedByte();
        int length = Math.max(0, readInt());
        ListTag<Tag<?>> listTag = new ListTag<>(length);

        for (int i = 0; i < length; i++)
            listTag.add(readTag((byte) listType, depth));

        return listTag;
    }

    @Override
    public @NotNull CompoundTag readCompoundTag(int depth) throws IOException {
        if (++depth >= 512)
            throw new NbtMaxDepthException();

        CompoundTag compoundTag = new CompoundTag();

        for (int id = readUnsignedByte(); id != 0; id = readUnsignedByte()) {
            String key = readUTF();
            Tag<?> tag = readTag((byte) id, depth);
            compoundTag.put(key, tag);
        }

        return compoundTag;
    }

}
