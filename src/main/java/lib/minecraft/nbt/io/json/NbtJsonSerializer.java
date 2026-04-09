package dev.sbs.minecraftapi.nbt.io.json;

import com.google.gson.stream.JsonWriter;
import dev.sbs.minecraftapi.nbt.exception.NbtMaxDepthException;
import dev.sbs.minecraftapi.nbt.io.NbtOutput;
import dev.sbs.minecraftapi.nbt.tags.Tag;
import dev.sbs.minecraftapi.nbt.tags.collection.CompoundTag;
import dev.sbs.minecraftapi.nbt.tags.collection.ListTag;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

/**
 * NBT JSON serialization that writes directly to a JSON writer.
 *
 * <p>Produces plain JSON with no SNBT-style type suffixes or array headers: numbers are emitted
 * as raw JSON numbers, {@code TAG_List} and the typed primitive arrays ({@code TAG_Byte_Array},
 * {@code TAG_Int_Array}, {@code TAG_Long_Array}) all serialize to plain JSON arrays, and
 * {@code TAG_Compound} serializes to a JSON object. Booleans are emitted as the numeric literals
 * {@code 1} / {@code 0} to match the on-disk {@link dev.sbs.minecraftapi.nbt.tags.primitive.ByteTag ByteTag}
 * representation.</p>
 *
 * <p>This representation is convenient for tools that expect ordinary JSON, but it does not
 * preserve NBT tag type information. Round-tripping through {@link NbtJsonDeserializer}
 * reconstructs types using the Minecraft Wiki's "Conversion from JSON" cascade, so a
 * {@code ShortTag(100)} will come back as a {@code ByteTag(100)}, a {@code ListTag<IntTag>} of
 * small values may come back as a {@code ByteArrayTag}, and floats that cannot be stored
 * exactly in {@code float} may come back as a {@code DoubleTag}. Use the binary or SNBT
 * backends when lossless round-trip is required.</p>
 *
 * @see NbtJsonDeserializer
 */
public class NbtJsonSerializer extends JsonWriter implements NbtOutput {

    public NbtJsonSerializer(@NotNull Writer writer) {
        super(writer);
        this.setIndent("    ");
    }

    @Override
    public void writeBoolean(boolean value) throws IOException {
        this.value(value ? 1 : 0);
    }

    @Override
    public void writeByte(int value) throws IOException {
        this.value(value);
    }

    @Override
    public void writeShort(int value) throws IOException {
        this.value(value);
    }

    @Override
    public void writeInt(int value) throws IOException {
        this.value(value);
    }

    @Override
    public void writeLong(long value) throws IOException {
        this.value(value);
    }

    @Override
    public void writeFloat(float value) throws IOException {
        this.value(value);
    }

    @Override
    public void writeDouble(double value) throws IOException {
        this.value(value);
    }

    @Override
    public void writeUTF(@NotNull String value) throws IOException {
        this.value(value);
    }

    @Override
    public void writeByteArray(byte @NotNull [] value) throws IOException {
        this.beginArray();

        for (byte b : value)
            this.value(b);

        this.endArray();
    }

    @Override
    public void writeIntArray(int @NotNull [] value) throws IOException {
        this.beginArray();

        for (int i : value)
            this.value(i);

        this.endArray();
    }

    @Override
    public void writeLongArray(long @NotNull [] value) throws IOException {
        this.beginArray();

        for (long l : value)
            this.value(l);

        this.endArray();
    }


    @Override
    public void writeListTag(@NotNull ListTag<Tag<?>> tag, int depth) throws IOException {
        if (++depth >= 512)
            throw new NbtMaxDepthException();

        this.beginArray();

        for (Tag<?> element : tag)
            this.writeTag(element, depth);

        this.endArray();
    }

    @Override
    public void writeCompoundTag(@NotNull CompoundTag tag, int depth) throws IOException {
        if (++depth >= 512)
            throw new NbtMaxDepthException();

        this.beginObject();

        for (Map.Entry<String, Tag<?>> entry : tag) {
            this.name(entry.getKey());
            this.writeTag(entry.getValue(), depth);
        }

        this.endObject();
    }

}
