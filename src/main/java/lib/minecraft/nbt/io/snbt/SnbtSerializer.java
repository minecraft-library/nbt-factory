package lib.minecraft.nbt.io.snbt;

import com.google.gson.stream.JsonWriter;
import lib.minecraft.nbt.exception.NbtMaxDepthException;
import lib.minecraft.nbt.io.NbtOutput;
import lib.minecraft.nbt.io.json.NbtJsonSerializer;
import lib.minecraft.nbt.tag.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static lib.minecraft.nbt.io.snbt.SnbtConstants.*;

/**
 * SNBT (stringified NBT) serialization that writes directly to a JSON writer, emitting the
 * type-preserving text format documented on the
 * <a href="https://minecraft.wiki/w/NBT_format">Minecraft Wiki NBT format</a> page.
 *
 * <p>Unlike {@link NbtJsonSerializer} which strips all tag
 * type information, SNBT keeps every tag disambiguated so it round-trips losslessly through
 * {@link SnbtDeserializer}. Type disambiguation rides on suffix letters for numeric literals
 * and on bracket-prefix markers for typed arrays:</p>
 *
 * <ul>
 *   <li><b>{@link ByteTag}</b> -
 *       numeric literal with a {@code b} suffix, e.g. {@code 34b}.</li>
 *   <li><b>{@link ShortTag}</b> -
 *       numeric literal with an {@code s} suffix, e.g. {@code 31415s}.</li>
 *   <li><b>{@link IntTag}</b> -
 *       numeric literal with no suffix, e.g. {@code 31415926}.</li>
 *   <li><b>{@link LongTag}</b> -
 *       numeric literal with an {@code l} suffix, e.g. {@code 31415926l}.</li>
 *   <li><b>{@link FloatTag}</b> -
 *       numeric literal with an {@code f} suffix, e.g. {@code 3.1415926f}.</li>
 *   <li><b>{@link DoubleTag}</b> -
 *       numeric literal with a {@code d} suffix, e.g. {@code 3.1415926d}.</li>
 *   <li><b>{@link ByteArrayTag}</b> -
 *       {@code [B;1b,2b,3b]}.</li>
 *   <li><b>{@link IntArrayTag}</b> -
 *       {@code [I;1,2,3]}.</li>
 *   <li><b>{@link LongArrayTag}</b> -
 *       {@code [L;1l,2l,3l]}.</li>
 *   <li><b>{@link ListTag}</b> -
 *       plain bracketed array {@code [value,value,...]}.</li>
 *   <li><b>{@link CompoundTag}</b> -
 *       braced JSON-like object {@code {key:value,...}}.</li>
 * </ul>
 *
 * <p>Strings are always quoted, as vanilla quotes them - with the one of {@code "} and {@code '}
 * the string does not meet first, so only that quote needs escaping, with the backslash and the
 * control characters escaped - so a string such as {@code true} never reads back as a byte.
 * Compound keys are written sorted, so equal compounds write equal text. Booleans are written as
 * {@code 1b} / {@code 0b}, the same representation Minecraft itself uses for boolean-valued NBT
 * fields. The output is compact unless constructed pretty, with a four-space indent.</p>
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

    /**
     * Hex digits of a control character written as a {@code \x} escape.
     */
    private static final @NotNull HexFormat HEX = HexFormat.of().withUpperCase();

    /**
     * Constructs a serializer writing compact SNBT.
     *
     * @param writer the destination
     */
    public SnbtSerializer(@NotNull Writer writer) {
        this(writer, false);
    }

    /**
     * Constructs a serializer writing compact SNBT, or SNBT indented four spaces per level.
     *
     * @param writer the destination
     * @param pretty whether to indent the output
     */
    public SnbtSerializer(@NotNull Writer writer, boolean pretty) {
        super(writer);

        if (pretty)
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
        this.jsonValue(quote(value));
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

        List<Map.Entry<String, Tag<?>>> entries = new ArrayList<>(tag.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        this.beginObject();

        for (Map.Entry<String, Tag<?>> entry : entries) {
            this.name(entry.getKey());
            this.writeTag(entry.getValue(), depth);
        }

        this.endObject();
    }

    /**
     * Quotes and escapes a string as vanilla does - with the one of {@code "} and {@code '} it does
     * not meet first, so only that quote needs escaping, and with {@code "} when it holds neither.
     *
     * @param value the string
     * @return the quoted string
     */
    private static @NotNull String quote(@NotNull String value) {
        StringBuilder builder = new StringBuilder(value.length() + 2).append(' ');
        char quote = 0;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            if (c == '\\')
                builder.append("\\\\");
            else if (c == '"' || c == '\'') {
                if (quote == 0)
                    quote = c == '"' ? '\'' : '"';

                if (quote == c)
                    builder.append('\\');

                builder.append(c);
            } else {
                String escape = controlEscape(c);

                if (escape != null)
                    builder.append('\\').append(escape);
                else
                    builder.append(c);
            }
        }

        if (quote == 0)
            quote = '"';

        builder.setCharAt(0, quote);
        return builder.append(quote).toString();
    }

    /**
     * Returns the escape a control character is written with, after the backslash.
     *
     * @param c the character
     * @return the escape, or {@code null} for a character written as it is
     */
    private static @Nullable String controlEscape(char c) {
        return switch (c) {
            case '\b' -> "b";
            case '\t' -> "t";
            case '\n' -> "n";
            case '\f' -> "f";
            case '\r' -> "r";
            default -> c < ' ' ? "x" + HEX.toHexDigits((byte) c) : null;
        };
    }

}
