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

public interface NbtInput extends MaxDepthIO {

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

    @NotNull ByteTag readByteTag() throws IOException;

    @NotNull ShortTag readShortTag() throws IOException;

    @NotNull IntTag readIntTag() throws IOException;

    @NotNull LongTag readLongTag() throws IOException;

    @NotNull FloatTag readFloatTag() throws IOException;

    @NotNull DoubleTag readDoubleTag() throws IOException;

    @NotNull ByteArrayTag readByteArrayTag() throws IOException;

    @NotNull StringTag readStringTag() throws IOException;

    default @NotNull ListTag<?> readListTag() throws IOException {
        return this.readListTag(0);
    }

    @NotNull ListTag<?> readListTag(int depth) throws IOException;

    default @NotNull CompoundTag readCompoundTag() throws IOException {
        return this.readCompoundTag(0);
    }

    @NotNull CompoundTag readCompoundTag(int depth) throws IOException;

    @NotNull IntArrayTag readIntArrayTag() throws IOException;

    @NotNull LongArrayTag readLongArrayTag() throws IOException;
    
}
