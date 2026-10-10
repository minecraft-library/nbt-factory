package lib.minecraft.nbt.io.snbt;

import lib.minecraft.nbt.exception.NbtMaxDepthException;
import lib.minecraft.nbt.io.NbtOutput;
import lib.minecraft.nbt.io.json.NbtJsonSerializer;
import lib.minecraft.nbt.tag.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static lib.minecraft.nbt.io.snbt.SnbtConstants.*;

/**
 * SNBT (stringified NBT) serialization that writes the text form vanilla's own tag rendering
 * writes, straight to a {@link Writer}.
 *
 * <p>Unlike {@link NbtJsonSerializer} which strips all tag type information, SNBT keeps every tag
 * disambiguated so it round-trips losslessly through {@link SnbtDeserializer}:</p>
 *
 * <ul>
 *   <li><b>Numbers</b> - the value with its type's suffix: {@code 1b}, {@code 1s}, {@code 1},
 *       {@code 1L}, {@code 1.0f}, {@code 1.0d}.</li>
 *   <li><b>Strings</b> - always quoted, with the one of {@code "} and {@code '} the string does not
 *       meet first, so only that quote needs escaping, and with the backslash and the control
 *       characters escaped - so a string such as {@code true} never reads back as a byte.</li>
 *   <li><b>Arrays</b> - {@code [B;1B,2B]}, {@code [I;1,2]}, {@code [L;1L,2L]}.</li>
 *   <li><b>Lists</b> - {@code [a,b]}, each element as the list holds it.</li>
 *   <li><b>Compounds</b> - {@code {a:1,b:2}}, the keys sorted so equal compounds write equal text,
 *       each bare where it is a plain identifier other than {@code true} or {@code false} and quoted
 *       otherwise.</li>
 * </ul>
 *
 * <p>Booleans are written as {@code 1b} / {@code 0b}, the representation Minecraft itself uses for
 * boolean-valued NBT fields. A control character with no escape of its own is written as
 * a {@code u00NN} escape rather than vanilla's {@code xNN}, the form {@link SnbtDeserializer} reads. The
 * output is compact unless constructed pretty, which breaks each entry onto its own line, indented
 * four spaces per level.</p>
 *
 * @see SnbtDeserializer
 * @see <a href="https://minecraft.wiki/w/NBT_format">Minecraft Wiki - NBT format - "SNBT format"</a>
 */
public class SnbtSerializer implements NbtOutput, Closeable, Flushable {

    /**
     * Keys written without quotes.
     */
    private static final @NotNull Pattern BARE_KEY = Pattern.compile("[A-Za-z._]+[A-Za-z0-9._+-]*");

    /**
     * Hex digits of a control character written as a {@code u00NN} escape.
     */
    private static final @NotNull HexFormat HEX = HexFormat.of().withUpperCase();

    /**
     * One level of pretty-printed indentation.
     */
    private static final @NotNull String INDENT = "    ";

    /**
     * The destination.
     */
    private final @NotNull Writer writer;

    /**
     * Whether each entry is broken onto its own, indented line.
     */
    private final boolean pretty;

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
        this.writer = writer;
        this.pretty = pretty;
    }

    @Override
    public void writeBoolean(boolean value) throws IOException {
        this.writeByte(value ? 1 : 0);
    }

    @Override
    public void writeByte(int value) throws IOException {
        this.writer.write(Integer.toString(value));
        this.writer.write('b');
    }

    @Override
    public void writeShort(int value) throws IOException {
        this.writer.write(Integer.toString(value));
        this.writer.write('s');
    }

    @Override
    public void writeInt(int value) throws IOException {
        this.writer.write(Integer.toString(value));
    }

    @Override
    public void writeLong(long value) throws IOException {
        this.writer.write(Long.toString(value));
        this.writer.write('L');
    }

    @Override
    public void writeFloat(float value) throws IOException {
        this.writer.write(Float.toString(value));
        this.writer.write('f');
    }

    @Override
    public void writeDouble(double value) throws IOException {
        this.writer.write(Double.toString(value));
        this.writer.write('d');
    }

    @Override
    public void writeUTF(@NotNull String value) throws IOException {
        this.writer.write(quote(value));
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
            sb.append(value[i]).append('B');
        }

        this.writer.write(sb.append(ARRAY_END).toString());
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
            sb.append(value[i]);
        }

        this.writer.write(sb.append(ARRAY_END).toString());
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
            sb.append(value[i]).append('L');
        }

        this.writer.write(sb.append(ARRAY_END).toString());
    }

    @Override
    public void writeListTag(@NotNull ListTag<Tag<?>> tag, int depth) throws IOException {
        if (++depth >= 512)
            throw new NbtMaxDepthException();

        this.writer.write(ARRAY_START);
        int index = 0;

        for (Tag<?> value : tag) {
            if (index++ > 0) this.writer.write(ENTRY_SEPARATOR);
            this.newline(depth);
            this.writeTag(value, depth);
        }

        if (index > 0) this.newline(depth - 1);
        this.writer.write(ARRAY_END);
    }

    @Override
    public void writeCompoundTag(@NotNull CompoundTag tag, int depth) throws IOException {
        if (++depth >= 512)
            throw new NbtMaxDepthException();

        List<Map.Entry<String, Tag<?>>> entries = new ArrayList<>(tag.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        this.writer.write('{');

        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, Tag<?>> entry = entries.get(i);
            if (i > 0) this.writer.write(ENTRY_SEPARATOR);
            this.newline(depth);
            this.writer.write(key(entry.getKey()));
            this.writer.write(this.pretty ? ": " : ":");
            this.writeTag(entry.getValue(), depth);
        }

        if (!entries.isEmpty()) this.newline(depth - 1);
        this.writer.write('}');
    }

    @Override
    public void flush() throws IOException {
        this.writer.flush();
    }

    @Override
    public void close() throws IOException {
        this.writer.close();
    }

    /**
     * Breaks the line and indents it to a nesting level, when pretty-printing.
     *
     * @param level the nesting level
     * @throws IOException if the destination refuses the write
     */
    private void newline(int level) throws IOException {
        if (this.pretty) {
            this.writer.write('\n');
            this.writer.write(INDENT.repeat(level));
        }
    }

    /**
     * Returns a compound key as written - bare where it is a plain identifier other than
     * {@code true} or {@code false}, and quoted otherwise.
     *
     * @param key the key
     * @return the key as written
     */
    private static @NotNull String key(@NotNull String key) {
        boolean bare = !key.equalsIgnoreCase("true") && !key.equalsIgnoreCase("false") && BARE_KEY.matcher(key).matches();
        return bare ? key : quote(key);
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
            default -> c < ' ' ? "u00" + HEX.toHexDigits((byte) c) : null;
        };
    }

}
