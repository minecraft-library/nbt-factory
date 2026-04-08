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

    /**
     * Dispatches a tag read by id directly to the matching primitive {@code readXxx} method.
     *
     * <p>Inlined - no intermediate {@code readXxxTag} wrapper hop. The tag instance is constructed
     * once with the primitive value, avoiding an extra getter call.</p>
     */
    default @NotNull Tag<?> readTag(byte id, int maxDepth) throws IOException {
        return switch (id) {
            case 1 -> new ByteTag(this.readByte());
            case 2 -> new ShortTag(this.readShort());
            case 3 -> new IntTag(this.readInt());
            case 4 -> new LongTag(this.readLong());
            case 5 -> new FloatTag(this.readFloat());
            case 6 -> new DoubleTag(this.readDouble());
            case 7 -> new ByteArrayTag(this.readByteArray());
            case 8 -> new StringTag(this.readUTF());
            case 9 -> this.readListTag(maxDepth);
            case 10 -> this.readCompoundTag(maxDepth);
            case 11 -> new IntArrayTag(this.readIntArray());
            case 12 -> new LongArrayTag(this.readLongArray());
            default -> throw new UnsupportedOperationException("Tag with id " + id + " is not supported.");
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

    byte @NotNull [] readByteArray() throws IOException;

    int @NotNull [] readIntArray() throws IOException;

    long @NotNull [] readLongArray() throws IOException;

    default @NotNull ListTag<?> readListTag() throws IOException {
        return this.readListTag(0);
    }

    @NotNull ListTag<?> readListTag(int depth) throws IOException;

    default @NotNull CompoundTag readCompoundTag() throws IOException {
        return this.readCompoundTag(0);
    }

    @NotNull CompoundTag readCompoundTag(int depth) throws IOException;

}
