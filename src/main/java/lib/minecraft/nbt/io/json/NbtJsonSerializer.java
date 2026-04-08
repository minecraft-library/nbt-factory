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
 * @apiNote Destroys tag type information, making deserialization unreliable, and is thus unimplemented.
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
