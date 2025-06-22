package dev.sbs.minecraftapi.nbt.io.stream;

import dev.sbs.minecraftapi.nbt.io.NbtInput;
import dev.sbs.minecraftapi.nbt.tags.Tag;
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
import dev.sbs.api.io.Compression;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Implementation for NBT deserialization.
 */
public class NbtInputStream extends DataInputStream implements NbtInput {

    public NbtInputStream(@NotNull InputStream inputStream) throws IOException {
        super(Compression.wrap(inputStream));
    }

    @SneakyThrows
    @Override
    public @NotNull ByteTag readByteTag() {
        return new ByteTag(this.readByte());
    }

    @SneakyThrows
    @Override
    public @NotNull ShortTag readShortTag() {
        return new ShortTag(this.readShort());
    }

    @SneakyThrows
    @Override
    public @NotNull IntTag readIntTag() {
        return new IntTag(this.readInt());
    }

    @SneakyThrows
    @Override
    public @NotNull LongTag readLongTag() {
        return new LongTag(this.readLong());
    }

    @SneakyThrows
    @Override
    public @NotNull FloatTag readFloatTag() {
        return new FloatTag(this.readFloat());
    }

    @SneakyThrows
    @Override
    public @NotNull DoubleTag readDoubleTag() {
        return new DoubleTag(this.readDouble());
    }

    @SneakyThrows
    @Override
    public @NotNull ByteArrayTag readByteArrayTag() {
        byte[] tmp = new byte[this.readInt()];
        this.readFully(tmp);
        return new ByteArrayTag(PrimitiveUtil.wrap(tmp));
    }

    @SneakyThrows
    @Override
    public @NotNull StringTag readStringTag() {
        return new StringTag(this.readUTF());
    }

    @SneakyThrows
    @Override
    public @NotNull ListTag<?> readListTag(int depth) {
        ListTag<Tag<?>> listTag = new ListTag<>();
        int listType = this.readUnsignedByte();
        int length = Math.max(0, this.readInt());

        for (int i = 0; i < length; i++)
            listTag.add(this.readTag((byte) listType, this.incrementMaxDepth(depth)));

        return listTag;
    }

    @SneakyThrows
    @Override
    public @NotNull CompoundTag readCompoundTag(int depth) {
        CompoundTag compoundTag = new CompoundTag();

        for (int id = this.readUnsignedByte() & 0xFF; id != 0; id = this.readUnsignedByte() & 0xFF) {
            String key = this.readUTF();
            Tag<?> tag = this.readTag((byte) id, this.incrementMaxDepth(depth));
            compoundTag.put(key, tag);
        }

        return compoundTag;
    }

    @SneakyThrows
    @Override
    public @NotNull IntArrayTag readIntArrayTag() {
        int length = this.readInt();
        Integer[] data = new Integer[length];

        for (int i = 0; i < length; i++)
            data[i] = this.readInt();

        return new IntArrayTag(data);
    }

    @SneakyThrows
    @Override
    public @NotNull LongArrayTag readLongArrayTag() {
        int length = this.readInt();
        Long[] data = new Long[length];

        for (int i = 0; i < length; i++)
            data[i] = this.readLong();

        return new LongArrayTag(data);
    }

}
