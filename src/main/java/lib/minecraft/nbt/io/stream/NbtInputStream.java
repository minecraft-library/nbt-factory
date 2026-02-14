package dev.sbs.minecraftapi.nbt.io.stream;

import dev.sbs.api.io.stream.Compression;
import dev.sbs.api.util.PrimitiveUtil;
import dev.sbs.minecraftapi.nbt.exception.NbtMaxDepthException;
import dev.sbs.minecraftapi.nbt.io.NbtInput;
import dev.sbs.minecraftapi.nbt.tags.Tag;
import dev.sbs.minecraftapi.nbt.tags.collection.CompoundTag;
import dev.sbs.minecraftapi.nbt.tags.collection.ListTag;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * NBT deserialization that reads from an input stream.
 */
@SuppressWarnings("all")
public class NbtInputStream extends DataInputStream implements NbtInput {

    public NbtInputStream(@NotNull InputStream inputStream) throws IOException {
        super(Compression.wrap(inputStream));
    }

    @Override
    public @NotNull Byte[] readByteArray() throws IOException {
        byte[] tmp = new byte[this.readInt()];
        this.readFully(tmp);
        return PrimitiveUtil.wrap(tmp);
    }

    @Override
    public @NotNull Integer[] readIntArray() throws IOException {
        int length = this.readInt();
        Integer[] data = new Integer[length];

        for (int i = 0; i < length; i++)
            data[i] = this.readInt();

        return data;
    }

    @Override
    public @NotNull Long[] readLongArray() throws IOException {
        int length = this.readInt();
        Long[] data = new Long[length];

        for (int i = 0; i < length; i++)
            data[i] = this.readLong();

        return data;
    }

    @Override
    public @NotNull ListTag<?> readListTag(int depth) throws IOException {
        if (++depth >= 512)
            throw new NbtMaxDepthException();

        ListTag<Tag<?>> listTag = new ListTag<>();
        int listType = this.readUnsignedByte();
        int length = Math.max(0, this.readInt());

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
