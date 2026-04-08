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

    /**
     * Dispatches a {@link Tag} write to the matching primitive {@code writeXxx} method.
     *
     * <p>Inlined directly against the concrete tag types - no intermediate {@code writeXxxTag} wrapper hop,
     * no {@link Tag#getValue()} chain through wrapper boxes for the primitive cases.</p>
     */
    @SuppressWarnings("unchecked")
    default void writeTag(@NotNull Tag<?> tag, int maxDepth) throws IOException {
        switch (tag.getId()) {
            case 1 -> this.writeByte(((ByteTag) tag).getValue());
            case 2 -> this.writeShort(((ShortTag) tag).getValue());
            case 3 -> this.writeInt(((IntTag) tag).getValue());
            case 4 -> this.writeLong(((LongTag) tag).getValue());
            case 5 -> this.writeFloat(((FloatTag) tag).getValue());
            case 6 -> this.writeDouble(((DoubleTag) tag).getValue());
            case 7 -> this.writeByteArray(((ByteArrayTag) tag).getValue());
            case 8 -> this.writeUTF(((StringTag) tag).getValue());
            case 9 -> this.writeListTag((ListTag<Tag<?>>) tag, maxDepth);
            case 10 -> this.writeCompoundTag((CompoundTag) tag, maxDepth);
            case 11 -> this.writeIntArray(((IntArrayTag) tag).getValue());
            case 12 -> this.writeLongArray(((LongArrayTag) tag).getValue());
            default -> throw new UnsupportedOperationException("Tag with id " + tag.getId() + " is not supported.");
        }
    }

    void writeBoolean(boolean value) throws IOException;

    void writeByte(int value) throws IOException;

    void writeShort(int value) throws IOException;

    void writeInt(int value) throws IOException;

    void writeLong(long value) throws IOException;

    void writeFloat(float value) throws IOException;

    void writeDouble(double value) throws IOException;

    void writeUTF(@NotNull String value) throws IOException;

    void writeByteArray(byte @NotNull [] value) throws IOException;

    void writeIntArray(int @NotNull [] value) throws IOException;

    void writeLongArray(long @NotNull [] value) throws IOException;

    default void writeListTag(@NotNull ListTag<Tag<?>> tag) throws IOException {
        this.writeListTag(tag, 0);
    }

    void writeListTag(@NotNull ListTag<Tag<?>> tag, int depth) throws IOException;

    default void writeCompoundTag(@NotNull CompoundTag tag) throws IOException {
        this.writeCompoundTag(tag, 0);
    }

    void writeCompoundTag(@NotNull CompoundTag tag, int depth) throws IOException;

}
