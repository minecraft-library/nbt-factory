package dev.sbs.minecraftapi.nbt.io.stream;

import dev.sbs.api.util.PrimitiveUtil;
import dev.sbs.api.util.StringUtil;
import dev.sbs.minecraftapi.nbt.exception.NbtMaxDepthException;
import dev.sbs.minecraftapi.nbt.io.NbtOutput;
import dev.sbs.minecraftapi.nbt.tags.Tag;
import dev.sbs.minecraftapi.nbt.tags.TagType;
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
    public void writeByteArray(@NotNull Byte[] data) throws IOException {
        this.writeInt(data.length);
        this.write(PrimitiveUtil.unwrap(data));
    }

    @Override
    public void writeIntArray(@NotNull Integer[] data) throws IOException {
        this.writeInt(data.length);

        for (int value : data)
            this.writeInt(value);
    }

    @Override
    public void writeLongArray(@NotNull Long[] data) throws IOException {
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
            if (entry.getValue().getId() == TagType.END.getId())
                break;

            this.writeByte(entry.getValue().getId());
            this.writeUTF(StringUtil.stripToEmpty(entry.getKey()));
            this.writeTag(entry.getValue(), depth);
        }

        this.writeByte(0);
    }

}
