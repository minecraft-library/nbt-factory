package dev.sbs.minecraftapi.nbt.io.array;

import dev.sbs.minecraftapi.nbt.exception.NbtMaxDepthException;
import dev.sbs.minecraftapi.nbt.io.NbtOutput;
import dev.sbs.minecraftapi.nbt.tags.Tag;
import dev.sbs.minecraftapi.nbt.tags.collection.CompoundTag;
import dev.sbs.minecraftapi.nbt.tags.collection.ListTag;
import org.jetbrains.annotations.NotNull;

import java.io.DataOutput;
import java.io.IOException;
import java.io.UTFDataFormatException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * High-performance NBT serialization that writes directly to a byte buffer.
 */
public class NbtOutputBuffer implements NbtOutput, DataOutput {

    /**
     * Default initial capacity (32 KB) - sized to fit a typical SkyBlock item NBT payload after enrichment
     * without paying any resize {@code arraycopy} cost on the serialization hot path.
     */
    private static final int DEFAULT_INITIAL_CAPACITY = 32 * 1024;

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

    public byte[] toByteArray() {
        byte[] result = new byte[this.position];
        System.arraycopy(this.buffer, 0, result, 0, this.position);
        return result;
    }

    // Optimized DataOutput implementation
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
        this.buffer[this.position++] = (byte) (value >>> 8);
        this.buffer[this.position++] = (byte) value;
    }

    @Override
    public void writeInt(int value) {
        this.ensureCapacity(4);
        this.buffer[this.position++] = (byte) (value >>> 24);
        this.buffer[this.position++] = (byte) (value >>> 16);
        this.buffer[this.position++] = (byte) (value >>> 8);
        this.buffer[this.position++] = (byte) value;
    }

    @Override
    public void writeLong(long value) {
        this.ensureCapacity(8);
        this.buffer[this.position++] = (byte) (value >>> 56);
        this.buffer[this.position++] = (byte) (value >>> 48);
        this.buffer[this.position++] = (byte) (value >>> 40);
        this.buffer[this.position++] = (byte) (value >>> 32);
        this.buffer[this.position++] = (byte) (value >>> 24);
        this.buffer[this.position++] = (byte) (value >>> 16);
        this.buffer[this.position++] = (byte) (value >>> 8);
        this.buffer[this.position++] = (byte) value;
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
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);

        if (bytes.length > 65535)
            throw new UTFDataFormatException("UTF string too long: " + bytes.length);

        this.writeShort(bytes.length);
        this.write(bytes);
    }

    @Override
    public void writeByteArray(byte @NotNull [] value) {
        this.writeInt(value.length);
        this.write(value);
    }

    @Override
    public void writeIntArray(int @NotNull [] value) {
        this.writeInt(value.length);

        for (int i : value)
            this.writeInt(i);
    }

    @Override
    public void writeLongArray(long @NotNull [] value) {
        this.writeInt(value.length);

        for (long l : value)
            this.writeLong(l);
    }

    @Override
    public void writeListTag(@NotNull ListTag<Tag<?>> tag, int depth) throws IOException {
        if (++depth >= 512)
            throw new NbtMaxDepthException();

        this.writeByte(tag.getListType());
        this.writeInt(tag.size());

        for (Tag<?> element : tag)
            this.writeTag(element, depth);
    }

    @Override
    public void writeCompoundTag(@NotNull CompoundTag tag, int depth) throws IOException {
        if (++depth >= 512)
            throw new NbtMaxDepthException();

        for (Map.Entry<String, Tag<?>> entry : tag) {
            Tag<?> value = entry.getValue();
            this.writeByte(value.getId());
            this.writeUTF(entry.getKey());
            this.writeTag(value, depth);
        }

        this.writeByte(0);
    }

}
