package dev.sbs.minecraftapi.nbt.io;

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
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public interface NbtOutput {

    @SuppressWarnings("all")
    default void writeTag(@NotNull Tag<?> tag, int maxDepth) throws IOException {
        switch (tag.getId()) {
            case 1 -> this.writeByteTag((ByteTag) tag);
            case 2 -> this.writeShortTag((ShortTag) tag);
            case 3 -> this.writeIntTag((IntTag) tag);
            case 4 -> this.writeLongTag((LongTag) tag);
            case 5 -> this.writeFloatTag((FloatTag) tag);
            case 6 -> this.writeDoubleTag((DoubleTag) tag);
            case 7 -> this.writeByteArrayTag((ByteArrayTag) tag);
            case 8 -> this.writeStringTag((StringTag) tag);
            case 9 -> this.writeListTag((ListTag<Tag<?>>) tag, maxDepth);
            case 10 -> this.writeCompoundTag((CompoundTag) tag, maxDepth);
            case 11 -> this.writeIntArrayTag((IntArrayTag) tag);
            case 12 -> this.writeLongArrayTag((LongArrayTag) tag);
            default -> throw new UnsupportedOperationException(String.format("Tag with id %s is not supported.", tag.getId()));
        };
    }

    void writeBoolean(boolean value) throws IOException;

    void writeByte(int value) throws IOException;

    void writeShort(int value) throws IOException;

    void writeInt(int value) throws IOException;

    void writeLong(long value) throws IOException;

    void writeFloat(float value) throws IOException;

    void writeDouble(double value) throws IOException;

    void writeUTF(@NotNull String value) throws IOException;

    void writeByteArray(@NotNull Byte[] value) throws IOException;

    void writeIntArray(@NotNull Integer[] value) throws IOException;

    void writeLongArray(@NotNull Long[] value) throws IOException;

    default void writeByteTag(@NotNull ByteTag tag) throws IOException {
        this.writeByte(tag.getValue());
    }

    default void writeShortTag(@NotNull ShortTag tag) throws IOException {
        this.writeShort(tag.getValue());
    }

    default void writeIntTag(@NotNull IntTag tag) throws IOException {
        this.writeInt(tag.getValue());
    }

    default void writeLongTag(@NotNull LongTag tag) throws IOException {
        this.writeLong(tag.getValue());
    }

    default void writeFloatTag(@NotNull FloatTag tag) throws IOException {
        this.writeFloat(tag.getValue());
    }

    default void writeDoubleTag(@NotNull DoubleTag tag) throws IOException {
        this.writeDouble(tag.getValue());
    }

    default void writeStringTag(@NotNull StringTag tag) throws IOException {
        this.writeUTF(tag.getValue());
    }

    default void writeByteArrayTag(@NotNull ByteArrayTag tag) throws IOException {
        this.writeByteArray(tag.getValue());
    }

    default void writeIntArrayTag(@NotNull IntArrayTag tag) throws IOException {
        this.writeIntArray(tag.getValue());
    }

    default void writeLongArrayTag(@NotNull LongArrayTag tag) throws IOException {
        this.writeLongArray(tag.getValue());
    }

    default void writeListTag(@NotNull ListTag<Tag<?>> tag) throws IOException {
        this.writeListTag(tag, 0);
    }

    void writeListTag(@NotNull ListTag<Tag<?>> tag, int depth) throws IOException;

    default void writeCompoundTag(@NotNull CompoundTag tag) throws IOException {
        this.writeCompoundTag(tag, 0);
    }

    void writeCompoundTag(@NotNull CompoundTag tag, int depth) throws IOException;

}
