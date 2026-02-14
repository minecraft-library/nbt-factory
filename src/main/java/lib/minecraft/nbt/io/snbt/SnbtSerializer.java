package dev.sbs.minecraftapi.nbt.io.snbt;

import com.google.gson.stream.JsonWriter;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.StringUtil;
import dev.sbs.minecraftapi.nbt.exception.NbtMaxDepthException;
import dev.sbs.minecraftapi.nbt.io.NbtOutput;
import dev.sbs.minecraftapi.nbt.tags.Tag;
import dev.sbs.minecraftapi.nbt.tags.TagType;
import dev.sbs.minecraftapi.nbt.tags.collection.CompoundTag;
import dev.sbs.minecraftapi.nbt.tags.collection.ListTag;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.Objects;

import static dev.sbs.minecraftapi.nbt.io.snbt.SnbtUtil.*;

/**
 * Implementation for SNBT serialization.
 */
public class SnbtSerializer extends JsonWriter implements NbtOutput, Closeable {

    private static final Reflection<JsonWriter> JSON_REFLECTION = Reflection.of(JsonWriter.class);
    private final @NotNull Writer writer;

    public SnbtSerializer(@NotNull Writer writer) {
        super(writer);
        this.setIndent("    ");
        this.writer = Objects.requireNonNull(JSON_REFLECTION.getValue(Writer.class, this));
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
    public void writeByteArray(@NotNull Byte[] value) throws IOException {
        this.writeArray(value, SnbtUtil.ARRAY_PREFIX_BYTE, ARRAY_SUFFIX_BYTE);
    }

    @Override
    public void writeIntArray(@NotNull Integer[] value) throws IOException {
        this.writeArray(value, SnbtUtil.ARRAY_PREFIX_INT, ARRAY_SUFFIX_INT);
    }

    @Override
    public void writeLongArray(@NotNull Long[] value) throws IOException {
        this.writeArray(value, SnbtUtil.ARRAY_PREFIX_LONG, ARRAY_SUFFIX_LONG);
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
            if (entry.getValue().getId() == TagType.END.getId())
                break;

            this.name(StringUtil.stripToEmpty(entry.getKey()));
            this.writeTag(entry.getValue(), depth);
        }

        this.endObject();
    }

    private <T extends Number> void writeArray(@NotNull T[] array, char prefix, String suffix) throws IOException {
        this.writer.append(ARRAY_START).append(prefix).append(ARRAY_TYPE_INDICATOR);

        for (int i = 0; i < array.length; i++) {
            if (i > 0) this.writer.append(ENTRY_SEPARATOR);
            this.writer.append(array[i].toString()).append(suffix);
        }

        this.writer.append(ARRAY_END);
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
