package dev.sbs.minecraftapi.nbt.io.snbt;

import com.google.gson.stream.JsonWriter;
import dev.sbs.minecraftapi.nbt.io.NbtOutput;
import dev.sbs.minecraftapi.nbt.tags.Tag;
import dev.sbs.minecraftapi.nbt.tags.TagType;
import dev.sbs.minecraftapi.nbt.tags.array.ArrayTag;
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
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Implementation for SNBT serialization.
 */
public class SnbtSerializer extends JsonWriter implements NbtOutput, Closeable {

    private static final Reflection<JsonWriter> JSON_REFLECTION = Reflection.of(JsonWriter.class);
    private static final @NotNull Pattern NON_QUOTE_PATTERN = Pattern.compile("[a-zA-Z_.+\\-]+");
    private final @NotNull Writer writer;

    public SnbtSerializer(@NotNull Writer writer) {
        super(writer);
        this.setIndent("    ");
        this.writer = Objects.requireNonNull(JSON_REFLECTION.getValue(Writer.class, this));
    }

    @Override
    public void writeByteTag(@NotNull ByteTag tag) throws IOException {
        this.jsonValue(tag.getValue() + "b");
    }

    @Override
    public void writeShortTag(@NotNull ShortTag tag) throws IOException {
        this.jsonValue(tag.getValue() + "s");
    }

    @Override
    public void writeIntTag(@NotNull IntTag tag) throws IOException {
        this.jsonValue(Integer.toString(tag.getValue()));
    }

    @Override
    public void writeLongTag(@NotNull LongTag tag) throws IOException {
        this.jsonValue(tag.getValue() + "l");
    }

    @Override
    public void writeFloatTag(@NotNull FloatTag tag) throws IOException {
        this.jsonValue(tag.getValue() + "f");
    }

    @Override
    public void writeDoubleTag(@NotNull DoubleTag tag) throws IOException {
        this.jsonValue(tag.getValue() + "d");
    }

    @Override
    public void writeByteArrayTag(@NotNull ByteArrayTag tag) throws IOException {
        this.writeArray(tag, 'B');
    }

    @Override
    public void writeStringTag(@NotNull StringTag tag) throws IOException {
        this.jsonValue(escapeString(tag.getValue()));
    }

    @Override
    public void writeListTag(@NotNull ListTag<Tag<?>> tag, int depth) throws IOException {
        this.beginArray();

        for (Tag<?> value : tag)
            this.writeTag(value, this.incrementMaxDepth(depth));

        this.endArray();
    }

    @Override
    public void writeCompoundTag(@NotNull CompoundTag tag, int depth) throws IOException {
        this.beginObject();

        for (Map.Entry<String, Tag<?>> entry : tag) {
            if (entry.getValue().getId() == TagType.END.getId())
                break;

            this.name(StringUtil.stripToEmpty(entry.getKey()));
            this.writeTag(entry.getValue(), this.incrementMaxDepth(depth));
        }

        this.endObject();
    }

    @Override
    public void writeIntArrayTag(@NotNull IntArrayTag tag) throws IOException {
        this.writeArray(tag, 'I');
    }

    @Override
    public void writeLongArrayTag(@NotNull LongArrayTag tag) throws IOException {
        this.writeArray(tag, 'L');
    }

    private <T extends Number> void writeArray(@NotNull ArrayTag<T> arrayTag, char prefix) throws IOException {
        this.beginArray();
        this.writer.append(prefix).append(';');

        for (int i = 0; i < arrayTag.length(); i++)
            this.jsonValue(arrayTag.get(i).toString());

        this.endArray();
    }

    private static String escapeString(@NotNull String value) {
        if (!NON_QUOTE_PATTERN.matcher(value).matches()) {
            StringBuilder sb = new StringBuilder();
            sb.append('"');

            for (int i = 0; i < value.length(); i++) {
                char current = value.charAt(i);

                if (current == '\\' || current == '"')
                    sb.append('\\');

                sb.append(current);
            }

            sb.append('"');
            return sb.toString();
        }

        return value;
    }

}
