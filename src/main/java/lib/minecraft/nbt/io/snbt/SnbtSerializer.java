package lib.minecraft.nbt.io.snbt;

import com.google.gson.stream.JsonWriter;
import lib.minecraft.nbt.exception.NbtMaxDepthException;
import lib.minecraft.nbt.io.NbtOutput;
import lib.minecraft.nbt.tags.Tag;
import lib.minecraft.nbt.tags.collection.CompoundTag;
import lib.minecraft.nbt.tags.collection.ListTag;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;

import static lib.minecraft.nbt.io.snbt.SnbtConstants.*;

/**
 * SNBT (stringified NBT) serialization that writes directly to a JSON writer, emitting the
 * type-preserving text format documented on the
 * <a href="https://minecraft.wiki/w/NBT_format">Minecraft Wiki NBT format</a> page.
 *
 * <p>Unlike {@link lib.minecraft.nbt.io.json.NbtJsonSerializer} which strips all tag
 * type information, SNBT keeps every tag disambiguated so it round-trips losslessly through
 * {@link SnbtDeserializer}. Type disambiguation rides on suffix letters for numeric literals
 * and on bracket-prefix markers for typed arrays:</p>
 *
 * <ul>
 *   <li><b>{@link lib.minecraft.nbt.tags.primitive.ByteTag ByteTag}</b> -
 *       numeric literal with a {@code b} suffix, e.g. {@code 34b}.</li>
 *   <li><b>{@link lib.minecraft.nbt.tags.primitive.ShortTag ShortTag}</b> -
 *       numeric literal with an {@code s} suffix, e.g. {@code 31415s}.</li>
 *   <li><b>{@link lib.minecraft.nbt.tags.primitive.IntTag IntTag}</b> -
 *       numeric literal with no suffix, e.g. {@code 31415926}.</li>
 *   <li><b>{@link lib.minecraft.nbt.tags.primitive.LongTag LongTag}</b> -
 *       numeric literal with an {@code l} suffix, e.g. {@code 31415926l}.</li>
 *   <li><b>{@link lib.minecraft.nbt.tags.primitive.FloatTag FloatTag}</b> -
 *       numeric literal with an {@code f} suffix, e.g. {@code 3.1415926f}.</li>
 *   <li><b>{@link lib.minecraft.nbt.tags.primitive.DoubleTag DoubleTag}</b> -
 *       numeric literal with a {@code d} suffix, e.g. {@code 3.1415926d}.</li>
 *   <li><b>{@link lib.minecraft.nbt.tags.array.ByteArrayTag ByteArrayTag}</b> -
 *       {@code [B;1b,2b,3b]}.</li>
 *   <li><b>{@link lib.minecraft.nbt.tags.array.IntArrayTag IntArrayTag}</b> -
 *       {@code [I;1,2,3]}.</li>
 *   <li><b>{@link lib.minecraft.nbt.tags.array.LongArrayTag LongArrayTag}</b> -
 *       {@code [L;1l,2l,3l]}.</li>
 *   <li><b>{@link lib.minecraft.nbt.tags.collection.ListTag ListTag}</b> -
 *       plain bracketed array {@code [value,value,...]}.</li>
 *   <li><b>{@link lib.minecraft.nbt.tags.collection.CompoundTag CompoundTag}</b> -
 *       braced JSON-like object {@code {key:value,...}}.</li>
 * </ul>
 *
 * <p>Strings are emitted unquoted when their contents match {@code [A-Za-z0-9._+-]+}; otherwise
 * they are double-quoted with {@code "} and {@code \} escaped by a leading backslash, matching
 * what {@link SnbtDeserializer} accepts on read. Booleans are written as {@code 1b} / {@code 0b},
 * the same representation Minecraft itself uses for boolean-valued NBT fields. The output is
 * pretty-printed with a four-space indent (inherited from the parent {@link JsonWriter}
 * configuration) to aid manual inspection.</p>
 *
 * @see SnbtDeserializer
 * @see <a href="https://minecraft.wiki/w/NBT_format">Minecraft Wiki - NBT format - "SNBT format"</a>
 */
public class SnbtSerializer extends JsonWriter implements NbtOutput, Closeable {

    /**
     * Reusable scratch buffer for primitive numeric writes. Lets {@code writeByte} / {@code
     * writeShort} / {@code writeLong} / {@code writeFloat} / {@code writeDouble} build their
     * suffixed text representation without allocating a fresh {@code StringBuilder} per call.
     * The instance is single-threaded by contract (callers must not share the serializer across
     * threads, same as {@link JsonWriter}), so reset-and-reuse is safe.
     */
    private final StringBuilder primitiveBuf = new StringBuilder(24);

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
        this.jsonValue(this.formatSuffixed(Integer.toString(value), 'b'));
    }

    @Override
    public void writeShort(int value) throws IOException {
        this.jsonValue(this.formatSuffixed(Integer.toString(value), 's'));
    }

    @Override
    public void writeInt(int value) throws IOException {
        this.jsonValue(Integer.toString(value));
    }

    @Override
    public void writeLong(long value) throws IOException {
        this.jsonValue(this.formatSuffixed(Long.toString(value), 'l'));
    }

    @Override
    public void writeFloat(float value) throws IOException {
        this.jsonValue(this.formatSuffixed(Float.toString(value), 'f'));
    }

    @Override
    public void writeDouble(double value) throws IOException {
        this.jsonValue(this.formatSuffixed(Double.toString(value), 'd'));
    }

    /**
     * Builds a numeric literal followed by a one-char SNBT type suffix using the reusable
     * {@link #primitiveBuf}, avoiding the per-call {@code value + "b"} string-concat
     * allocation that the prior implementation performed.
     *
     * @param numeric the already-formatted numeric body (e.g. {@code Integer.toString(value)})
     * @param suffix the SNBT type suffix character to append
     * @return the suffixed literal as a freshly-allocated string ready for {@code jsonValue}
     */
    private String formatSuffixed(@NotNull String numeric, char suffix) {
        StringBuilder buf = this.primitiveBuf;
        buf.setLength(0);
        buf.append(numeric).append(suffix);
        return buf.toString();
    }

    @Override
    public void writeUTF(@NotNull String value) throws IOException {
        this.jsonValue(escapeString(value));
    }

    @Override
    public void writeByteArray(byte @NotNull [] value) throws IOException {
        // Per-byte cost: up to 4 chars for "-128", 1 for the suffix, 1 for the separator.
        StringBuilder sb = new StringBuilder(8 + value.length * 6)
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
        // Per-int cost: up to 11 chars for "-2147483648", 1 for the separator.
        StringBuilder sb = new StringBuilder(8 + value.length * 12)
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
        // Per-long cost: up to 20 chars for "-9223372036854775808", 1 for the suffix, 1 for the separator.
        StringBuilder sb = new StringBuilder(8 + value.length * 22)
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
        // Single-pass scan: find the first character that requires quoting. Mirrors the prior
        // NON_QUOTE_PATTERN [a-zA-Z_.+\-] check character-by-character via the IS_NON_QUOTE
        // table, but bails out at the first offender so an unquoted-eligible string never pays
        // the cost of a full re-walk.
        int len = value.length();
        if (len == 0)
            return "\"\"";

        int firstOffending = -1;
        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            if (c >= 128 || !IS_NON_QUOTE[c]) {
                firstOffending = i;
                break;
            }
        }

        // Fast path: every character was unquoted-eligible, so emit the value verbatim.
        if (firstOffending == -1)
            return value;

        // Slow path: at least one character requires quoting. Pre-size for {@code "value"} plus
        // a small allowance for the escape backslashes.
        StringBuilder sb = new StringBuilder(len + 4);
        sb.append(STRING_DELIMITER_1);

        // Copy the prefix that was already verified clean - no escape check needed inside it
        // because IS_NON_QUOTE excludes both STRING_ESCAPE ('\') and STRING_DELIMITER_1 ('"').
        if (firstOffending > 0)
            sb.append(value, 0, firstOffending);

        // Walk the remaining tail char-by-char, escaping the two SNBT escapees.
        for (int i = firstOffending; i < len; i++) {
            char c = value.charAt(i);
            if (c == STRING_ESCAPE || c == STRING_DELIMITER_1)
                sb.append(STRING_ESCAPE);
            sb.append(c);
        }

        sb.append(STRING_DELIMITER_1);
        return sb.toString();
    }

}
