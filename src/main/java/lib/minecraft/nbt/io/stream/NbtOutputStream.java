package dev.sbs.minecraftapi.nbt.io.stream;

import dev.sbs.minecraftapi.nbt.exception.NbtMaxDepthException;
import dev.sbs.minecraftapi.nbt.io.NbtOutput;
import dev.sbs.minecraftapi.nbt.tags.Tag;
import dev.sbs.minecraftapi.nbt.tags.collection.CompoundTag;
import dev.sbs.minecraftapi.nbt.tags.collection.ListTag;
import org.jetbrains.annotations.NotNull;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

/**
 * NBT serialization that writes directly to an output stream.
 */
public class NbtOutputStream extends DataOutputStream implements NbtOutput {

    public NbtOutputStream(@NotNull OutputStream outputStream) {
        super(outputStream);
    }

    @Override
    public void writeByteArray(byte @NotNull [] data) throws IOException {
        this.writeInt(data.length);
        this.write(data);
    }

    @Override
    public void writeIntArray(int @NotNull [] data) throws IOException {
        this.writeInt(data.length);

        for (int value : data)
            this.writeInt(value);
    }

    @Override
    public void writeLongArray(long @NotNull [] data) throws IOException {
        this.writeInt(data.length);

        for (long value : data)
            this.writeLong(value);
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
