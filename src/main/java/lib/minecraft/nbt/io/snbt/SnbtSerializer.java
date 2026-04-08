package dev.sbs.minecraftapi.nbt.io.snbt;

import com.google.gson.stream.JsonWriter;
import dev.sbs.minecraftapi.nbt.exception.NbtMaxDepthException;
import dev.sbs.minecraftapi.nbt.io.NbtOutput;
import dev.sbs.minecraftapi.nbt.tags.Tag;
import dev.sbs.minecraftapi.nbt.tags.collection.CompoundTag;
import dev.sbs.minecraftapi.nbt.tags.collection.ListTag;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;

import static dev.sbs.minecraftapi.nbt.io.snbt.SnbtUtil.*;

/**
 * Implementation for SNBT serialization.
 */
public class SnbtSerializer extends JsonWriter implements NbtOutput, Closeable {

    public SnbtSerializer(@NotNull Writer writer) {
        super(writer);
        this.setIndent("    ");
    }

    @Override
    public void writeBoolean(boolean value) throws IOException {
        this.writeByte(value ? 1 : 0);
    }

    @Override
    public void writeByte(int value) throws IOException {
        this.jsonValue(value + "b");
    }

    @Override
    public void writeShort(int value) throws IOException {
        this.jsonValue(value + "s");
    }

    @Override
    public void writeInt(int value) throws IOException {
        this.jsonValue(Integer.toString(value));
    }

    @Override
    public void writeLong(long value) throws IOException {
        this.jsonValue(value + "l");
    }

    @Override
    public void writeFloat(float value) throws IOException {
        this.jsonValue(value + "f");
    }

    @Override
    public void writeDouble(double value) throws IOException {
        this.jsonValue(value + "d");
    }

    @Override
    public void writeUTF(@NotNull String value) throws IOException {
        this.jsonValue(escapeString(value));
    }

    @Override
    public void writeByteArray(byte @NotNull [] value) throws IOException {
        StringBuilder sb = new StringBuilder()
            .append(ARRAY_START)
            .append(ARRAY_PREFIX_BYTE)
            .append(ARRAY_TYPE_INDICATOR);

        for (int i = 0; i < value.length; i++) {
            if (i > 0) sb.append(ENTRY_SEPARATOR);
            sb.append(value[i]).append(ARRAY_SUFFIX_BYTE);
        }

        sb.append(ARRAY_END);
        this.jsonValue(sb.toString());
    }

    @Override
    public void writeIntArray(int @NotNull [] value) throws IOException {
        StringBuilder sb = new StringBuilder()
            .append(ARRAY_START)
            .append(ARRAY_PREFIX_INT)
            .append(ARRAY_TYPE_INDICATOR);

        for (int i = 0; i < value.length; i++) {
            if (i > 0) sb.append(ENTRY_SEPARATOR);
            sb.append(value[i]).append(ARRAY_SUFFIX_INT);
        }

        sb.append(ARRAY_END);
        this.jsonValue(sb.toString());
    }

    @Override
    public void writeLongArray(long @NotNull [] value) throws IOException {
        StringBuilder sb = new StringBuilder()
            .append(ARRAY_START)
            .append(ARRAY_PREFIX_LONG)
            .append(ARRAY_TYPE_INDICATOR);

        for (int i = 0; i < value.length; i++) {
            if (i > 0) sb.append(ENTRY_SEPARATOR);
            sb.append(value[i]).append(ARRAY_SUFFIX_LONG);
        }

        sb.append(ARRAY_END);
        this.jsonValue(sb.toString());
    }

    @Override
    public void writeListTag(@NotNull ListTag<Tag<?>> tag, int depth) throws IOException {
        if (++depth >= 512)
            throw new NbtMaxDepthException();

        this.beginArray();

        for (Tag<?> value : tag)
            this.writeTag(value, depth);

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

    private static String escapeString(@NotNull String value) {
        if (!NON_QUOTE_PATTERN.matcher(value).matches()) {
            StringBuilder sb = new StringBuilder();
            sb.append(STRING_DELIMITER_1);

            for (int i = 0; i < value.length(); i++) {
                char current = value.charAt(i);

                if (current == STRING_ESCAPE || current == STRING_DELIMITER_1)
                    sb.append(STRING_ESCAPE);

                sb.append(current);
            }

            sb.append(STRING_DELIMITER_1);
            return sb.toString();
        }

        return value;
    }

}
