package dev.sbs.minecraftapi.nbt.io.stream;

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
import dev.sbs.api.util.PrimitiveUtil;
import dev.sbs.api.util.StringUtil;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;

import java.io.DataOutputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * Implementation for NBT serialization.
 */
public class NbtOutputStream extends DataOutputStream implements NbtOutput {

    public NbtOutputStream(@NotNull OutputStream outputStream) {
        super(outputStream);
    }

    @SneakyThrows
    @Override
    public void writeByteTag(@NotNull ByteTag tag) {
        this.writeByte(tag.getValue());
    }

    @SneakyThrows
    @Override
    public void writeShortTag(@NotNull ShortTag tag) {
        this.writeShort(tag.getValue());
    }

    @SneakyThrows
    @Override
    public void writeIntTag(@NotNull IntTag tag) {
        this.writeInt(tag.getValue());
    }

    @SneakyThrows
    @Override
    public void writeLongTag(@NotNull LongTag tag) {
        this.writeLong(tag.getValue());
    }

    @SneakyThrows
    @Override
    public void writeFloatTag(@NotNull FloatTag tag) {
        this.writeFloat(tag.getValue());
    }

    @SneakyThrows
    @Override
    public void writeDoubleTag(@NotNull DoubleTag tag) {
        this.writeDouble(tag.getValue());
    }

    @SneakyThrows
    @Override
    public void writeByteArrayTag(@NotNull ByteArrayTag tag) {
        this.writeInt(tag.length());
        this.write(PrimitiveUtil.unwrap(tag.getValue()));
    }

    @SneakyThrows
    @Override
    public void writeStringTag(@NotNull StringTag tag) {
        this.writeUTF(tag.getValue());
    }

    @SneakyThrows
    @Override
    public void writeListTag(@NotNull ListTag<Tag<?>> tag, int depth) {
        this.writeByte(tag.getListType());
        this.writeInt(tag.size());

        for (Tag<?> element : tag)
            this.writeTag(element, this.incrementMaxDepth(depth));
    }

    @SneakyThrows
    @Override
    public void writeCompoundTag(@NotNull CompoundTag tag, int depth) {
        for (Map.Entry<String, Tag<?>> entry : tag) {
            if (entry.getValue().getId() == TagType.END.getId())
                break;

            this.writeByte(entry.getValue().getId());
            this.writeUTF(StringUtil.stripToEmpty(entry.getKey()));
            this.writeTag(entry.getValue(), this.incrementMaxDepth(depth));
        }

        this.writeByte(0);
    }

    @SneakyThrows
    @Override
    public void writeIntArrayTag(@NotNull IntArrayTag tag) {
        this.writeInt(tag.length());

        for (int i : tag.getValue())
            this.writeInt(i);
    }

    @SneakyThrows
    @Override
    public void writeLongArrayTag(@NotNull LongArrayTag tag) {
        this.writeInt(tag.length());

        for (long i : tag.getValue())
            this.writeLong(i);
    }

}
