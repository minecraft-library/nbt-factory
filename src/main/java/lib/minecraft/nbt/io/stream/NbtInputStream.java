package dev.sbs.minecraftapi.nbt.io.stream;

import dev.sbs.minecraftapi.nbt.exception.NbtMaxDepthException;
import dev.sbs.minecraftapi.nbt.io.NbtInput;
import dev.sbs.minecraftapi.nbt.tags.Tag;
import dev.sbs.minecraftapi.nbt.tags.collection.CompoundTag;
import dev.sbs.minecraftapi.nbt.tags.collection.ListTag;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * NBT deserialization that reads from an input stream.
 *
 * <p>Wraps the provided stream in a {@link BufferedInputStream} unless it is already buffered, so callers
 * passing raw {@code FileInputStream}, {@code GZIPInputStream}, or socket streams do not pay per-byte
 * syscall overhead through {@link DataInputStream}.</p>
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

        int listType = this.readUnsignedByte();
        int length = Math.max(0, this.readInt());
        ListTag<Tag<?>> listTag = new ListTag<>(length);

        for (int i = 0; i < length; i++)
            listTag.add(this.readTag((byte) listType, depth));

        return listTag;
    }

    @Override
    public @NotNull CompoundTag readCompoundTag(int depth) throws IOException {
        if (++depth >= 512)
            throw new NbtMaxDepthException();

        CompoundTag compoundTag = new CompoundTag();

        for (int id = this.readUnsignedByte() & 0xFF; id != 0; id = this.readUnsignedByte() & 0xFF) {
            String key = this.readUTF();
            Tag<?> tag = this.readTag((byte) id, depth);
            compoundTag.put(key, tag);
        }

        return compoundTag;
    }

}
