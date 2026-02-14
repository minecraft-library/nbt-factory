package dev.sbs.minecraftapi.nbt.io.stream;

import dev.sbs.api.util.PrimitiveUtil;
import dev.sbs.api.util.StringUtil;
import dev.sbs.minecraftapi.nbt.io.NbtOutput;
import dev.sbs.minecraftapi.nbt.tags.Tag;
import dev.sbs.minecraftapi.nbt.tags.TagType;
import dev.sbs.minecraftapi.nbt.tags.array.ByteArrayTag;
import dev.sbs.minecraftapi.nbt.tags.array.IntArrayTag;
import dev.sbs.minecraftapi.nbt.tags.array.LongArrayTag;
import dev.sbs.minecraftapi.nbt.tags.collection.CompoundTag;
import dev.sbs.minecraftapi.nbt.tags.collection.ListTag;
import dev.sbs.minecraftapi.nbt.tags.primitive.ByteTag;
import dev.sbs.minecraftapi.nbt.tags.primitive.DoubleTag;
import dev.sbs.minecraftapi.nbt.tags.primitive.FloatTag;
import dev.sbs.minecraftapi.nbt.tags.primitive.IntTag;
import dev.sbs.minecraftapi.nbt.tags.primitive.LongTag;
import dev.sbs.minecraftapi.nbt.tags.primitive.ShortTag;
import dev.sbs.minecraftapi.nbt.tags.primitive.StringTag;
import org.jetbrains.annotations.NotNull;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

/**
 * Implementation for NBT serialization.
 */
public class NbtOutputStream extends DataOutputStream implements NbtOutput {

    public NbtOutputStream(@NotNull OutputStream outputStream) {
        super(outputStream);
    }

    @Override
    public void writeByteTag(@NotNull ByteTag tag) throws IOException {
        this.writeByte(tag.getValue());
    }

    @Override
    public void writeShortTag(@NotNull ShortTag tag) throws IOException {
        this.writeShort(tag.getValue());
    }

    @Override
    public void writeIntTag(@NotNull IntTag tag) throws IOException {
        this.writeInt(tag.getValue());
    }

    @Override
    public void writeLongTag(@NotNull LongTag tag) throws IOException {
        this.writeLong(tag.getValue());
    }

    @Override
    public void writeFloatTag(@NotNull FloatTag tag) throws IOException {
        this.writeFloat(tag.getValue());
    }

    @Override
    public void writeDoubleTag(@NotNull DoubleTag tag) throws IOException {
        this.writeDouble(tag.getValue());
    }

    @Override
    public void writeByteArrayTag(@NotNull ByteArrayTag tag) throws IOException {
        this.writeInt(tag.length());
        this.write(PrimitiveUtil.unwrap(tag.getValue()));
    }

    @Override
    public void writeStringTag(@NotNull StringTag tag) throws IOException {
        this.writeUTF(tag.getValue());
    }

    @Override
    public void writeListTag(@NotNull ListTag<Tag<?>> tag, int depth) throws IOException {
        this.writeByte(tag.getListType());
        this.writeInt(tag.size());

        for (Tag<?> element : tag)
            this.writeTag(element, this.incrementMaxDepth(depth));
    }

    @Override
    public void writeCompoundTag(@NotNull CompoundTag tag, int depth) throws IOException {
        for (Map.Entry<String, Tag<?>> entry : tag) {
            if (entry.getValue().getId() == TagType.END.getId())
                break;

            this.writeByte(entry.getValue().getId());
            this.writeUTF(StringUtil.stripToEmpty(entry.getKey()));
            this.writeTag(entry.getValue(), this.incrementMaxDepth(depth));
        }

        this.writeByte(0);
    }

    @Override
    public void writeIntArrayTag(@NotNull IntArrayTag tag) throws IOException {
        this.writeInt(tag.length());

        for (int i : tag.getValue())
            this.writeInt(i);
    }

    @Override
    public void writeLongArrayTag(@NotNull LongArrayTag tag) throws IOException {
        this.writeInt(tag.length());

        for (long i : tag.getValue())
            this.writeLong(i);
    }

}
