package dev.sbs.minecraftapi.nbt.io;

import dev.sbs.minecraftapi.nbt.exception.NbtMaxDepthException;
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

    /**
     * Reads an NBT {@code TAG_List} payload: element type byte, big-endian length, then that many
     * elements of the given type.
     *
     * <p>Binary NBT backends ({@code NbtInputBuffer}, {@code NbtInputStream}) share this
     * implementation. SNBT and any other text-based backend overrides with format-specific parsing.</p>
     */
    default @NotNull ListTag<?> readListTag(int depth) throws IOException {
        if (++depth >= 512)
            throw new NbtMaxDepthException();

        byte listType = this.readByte();
        int length = Math.max(0, this.readInt());
        // Pre-seed elementId so ListTag.add skips the "first element" probe on every entry.
        ListTag<Tag<?>> listTag = new ListTag<>(listType, length);

        for (int i = 0; i < length; i++)
            listTag.add(this.readTag(listType, depth));

        return listTag;
    }

    default @NotNull CompoundTag readCompoundTag() throws IOException {
        return this.readCompoundTag(0);
    }

    /**
     * Reads an NBT {@code TAG_Compound} payload: a sequence of {@code (type, name, value)} entries
     * terminated by a {@code TAG_End} (id 0).
     *
     * <p>Binary NBT backends share this implementation. SNBT and other text-based backends override
     * with format-specific parsing.</p>
     */
    default @NotNull CompoundTag readCompoundTag(int depth) throws IOException {
        if (++depth >= 512)
            throw new NbtMaxDepthException();

        CompoundTag compoundTag = new CompoundTag();

        // readByte() & 0xFF is the unsigned-byte form. Avoids making readUnsignedByte abstract
        // on this interface, which would force SnbtDeserializer (whose readByte parses text) to
        // provide a meaningless implementation.
        for (int id = this.readByte() & 0xFF; id != 0; id = this.readByte() & 0xFF) {
            String key = this.readUTF();
            Tag<?> tag = this.readTag((byte) id, depth);
            compoundTag.put(key, tag);
        }

        return compoundTag;
    }

}
