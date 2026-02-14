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

public interface NbtInput {

    default @NotNull Tag<?> readTag(byte id, int maxDepth) throws IOException {
        return switch (id) {
            case 1 -> this.readByteTag();
            case 2 -> this.readShortTag();
            case 3 -> this.readIntTag();
            case 4 -> this.readLongTag();
            case 5 -> this.readFloatTag();
            case 6 -> this.readDoubleTag();
            case 7 -> this.readByteArrayTag();
            case 8 -> this.readStringTag();
            case 9 -> this.readListTag(maxDepth);
            case 10 -> this.readCompoundTag(maxDepth);
            case 11 -> this.readIntArrayTag();
            case 12 -> this.readLongArrayTag();
            default -> throw new UnsupportedOperationException(String.format("Tag with id %s is not supported.", id));
        };
    }

    boolean readBoolean() throws IOException;

    byte readByte() throws IOException;

    short readShort() throws IOException;

    int readInt() throws IOException;

    long readLong() throws IOException;

    float readFloat() throws IOException;

    double readDouble() throws IOException;

    @NotNull String readUTF() throws IOException;

    @NotNull Byte[] readByteArray() throws IOException;

    @NotNull Integer[] readIntArray() throws IOException;

    @NotNull Long[] readLongArray() throws IOException;

    default @NotNull ByteTag readByteTag() throws IOException {
        return new ByteTag(this.readByte());
    }

    default @NotNull ShortTag readShortTag() throws IOException {
        return new ShortTag(this.readShort());
    }

    default @NotNull IntTag readIntTag() throws IOException {
        return new IntTag(this.readInt());
    }

    default @NotNull LongTag readLongTag() throws IOException {
        return new LongTag(this.readLong());
    }

    default @NotNull FloatTag readFloatTag() throws IOException {
        return new FloatTag(this.readFloat());
    }

    default @NotNull DoubleTag readDoubleTag() throws IOException {
        return new DoubleTag(this.readDouble());
    }

    default @NotNull ByteArrayTag readByteArrayTag() throws IOException {
        return new ByteArrayTag(this.readByteArray());
    }

    default @NotNull StringTag readStringTag() throws IOException {
        return new StringTag(this.readUTF());
    }

    default @NotNull ListTag<?> readListTag() throws IOException {
        return this.readListTag(0);
    }

    @NotNull ListTag<?> readListTag(int depth) throws IOException;

    default @NotNull CompoundTag readCompoundTag() throws IOException {
        return this.readCompoundTag(0);
    }

    @NotNull CompoundTag readCompoundTag(int depth) throws IOException;

    default @NotNull IntArrayTag readIntArrayTag() throws IOException {
        return new IntArrayTag(this.readIntArray());
    }

    default @NotNull LongArrayTag readLongArrayTag() throws IOException {
        return new LongArrayTag(this.readLongArray());
    }
    
}
