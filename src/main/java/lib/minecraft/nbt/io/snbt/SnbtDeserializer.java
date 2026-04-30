package lib.minecraft.nbt.io.snbt;

import lib.minecraft.nbt.exception.NbtMaxDepthException;
import lib.minecraft.nbt.io.NbtInput;
import lib.minecraft.nbt.io.util.ByteList;
import lib.minecraft.nbt.io.util.IntList;
import lib.minecraft.nbt.io.util.LongList;
import lib.minecraft.nbt.tags.Tag;
import lib.minecraft.nbt.tags.TagType;
import lib.minecraft.nbt.tags.collection.CompoundTag;
import lib.minecraft.nbt.tags.collection.ListTag;
import dev.simplified.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.StringReader;

import static lib.minecraft.nbt.io.snbt.SnbtConstants.*;

/**
 * SNBT (stringified NBT) deserialization that reads from a {@link StringReader} and
 * reconstructs the full Minecraft NBT tag tree without loss of type information.
 *
 * <p>SNBT is the type-preserving text companion to binary NBT, defined on the
 * <a href="https://minecraft.wiki/w/NBT_format">Minecraft Wiki NBT format</a> page. Every
 * numeric literal carries its own type suffix, every typed array carries a prefix marker, and
 * every compound / list structure matches the binary framing verbatim - so a round trip through
 * this deserializer and {@link SnbtSerializer} yields the exact same tag tree, byte-for-byte
 * equivalent to the binary wire format.</p>
 *
 * <p>Type reconstruction rules (case-insensitive suffixes):</p>
 * <ul>
 *   <li><b>Numeric literal with suffix</b> - resolves directly to the matching primitive tag:
 *       {@code 34b} to {@link lib.minecraft.nbt.tags.primitive.ByteTag ByteTag},
 *       {@code 31415s} to {@link lib.minecraft.nbt.tags.primitive.ShortTag ShortTag},
 *       {@code 31415926l} to {@link lib.minecraft.nbt.tags.primitive.LongTag LongTag},
 *       {@code 3.14f} to {@link lib.minecraft.nbt.tags.primitive.FloatTag FloatTag},
 *       {@code 3.14d} to {@link lib.minecraft.nbt.tags.primitive.DoubleTag DoubleTag}.</li>
 *   <li><b>Numeric literal without suffix</b> - {@link lib.minecraft.nbt.tags.primitive.IntTag IntTag}
 *       when the literal has no decimal point,
 *       {@link lib.minecraft.nbt.tags.primitive.DoubleTag DoubleTag} when it does.</li>
 *   <li><b>Quoted string</b> - {@link lib.minecraft.nbt.tags.primitive.StringTag StringTag}
 *       always, even when the contents look numeric. Either {@code "text"} or {@code 'text'}
 *       delimiters are accepted; {@code \"}, {@code \\}, and {@code \'} escape sequences are
 *       unescaped character-for-character.</li>
 *   <li><b>Unquoted identifier</b> - classified by a single-pass scan that dispatches on the
 *       trailing suffix character ({@code b/B/s/S/l/L/f/F/d/D}) and validates the leading
 *       numeric body; falls back to
 *       {@link lib.minecraft.nbt.tags.primitive.StringTag StringTag} on no match. Valid
 *       unquoted characters are {@code [A-Za-z0-9._+-]}.</li>
 *   <li><b>{@code [B;...]}</b> /
 *       <b>{@code [I;...]}</b> /
 *       <b>{@code [L;...]}</b> - typed arrays
 *       ({@link lib.minecraft.nbt.tags.array.ByteArrayTag ByteArrayTag} /
 *       {@link lib.minecraft.nbt.tags.array.IntArrayTag IntArrayTag} /
 *       {@link lib.minecraft.nbt.tags.array.LongArrayTag LongArrayTag}).</li>
 *   <li><b>{@code [value,value,...]}</b> -
 *       {@link lib.minecraft.nbt.tags.collection.ListTag ListTag} whose element type is
 *       decided from the first element and then enforced for the rest via
 *       {@link lib.minecraft.nbt.tags.collection.ListTag#add(Tag) ListTag.add}.</li>
 *   <li><b>{@code {key:value,...}}</b> -
 *       {@link lib.minecraft.nbt.tags.collection.CompoundTag CompoundTag}.</li>
 * </ul>
 *
 * <p>Tag-type classification for list and compound children runs through {@code peekTagId()},
 * which uses {@link StringReader#mark(int)} / {@link StringReader#reset()} lookahead to identify
 * the next value without consuming it. The depth guard and
 * {@link lib.minecraft.nbt.exception.NbtMaxDepthException} behaviour match the binary
 * backends exactly: nesting deeper than 512 throws.</p>
 *
 * @see SnbtSerializer
 * @see <a href="https://minecraft.wiki/w/NBT_format">Minecraft Wiki - NBT format - "SNBT format"</a>
 */
public class SnbtDeserializer extends StringReader implements NbtInput {

    public SnbtDeserializer(@NotNull String snbt) {
        super(StringUtil.trimToEmpty(snbt));
    }

    @Override
    public boolean readBoolean() throws IOException {
        return this.readByte() != 0;
    }

    @Override
    public byte readByte() throws IOException {
        return Byte.parseByte(this.readNumberAsString());
    }

    @Override
    public short readShort() throws IOException {
        return Short.parseShort(this.readNumberAsString());
    }

    @Override
    public int readInt() throws IOException {
        return Integer.parseInt(this.readNumberAsString());
    }

    @Override
    public long readLong() throws IOException {
        return Long.parseLong(this.readNumberAsString());
    }

    @Override
    public float readFloat() throws IOException {
        return Float.parseFloat(this.readNumberAsString());
    }

    @Override
    public double readDouble() throws IOException {
        return Double.parseDouble(this.readNumberAsString());
    }

    /**
     * Read an SNBT string from the current index of a reader
     */
    @Override
    public @NotNull String readUTF() throws IOException {
        return this.readUTF(false);
    }

    @Override
    public byte @NotNull [] readByteArray() throws IOException {
        this.readArrayHeader(ARRAY_PREFIX_BYTE);
        ByteList values = new ByteList();

        do {
            this.skipWhitespace();

            this.mark(1);
            if (this.read() == ARRAY_END)
                break;
            this.reset();

            values.add(Byte.parseByte(this.readNumberAsString()));
            this.skipWhitespace();
        } while (this.read() == ENTRY_SEPARATOR);

        return values.toArray();
    }

    @Override
    public int @NotNull [] readIntArray() throws IOException {
        this.readArrayHeader(ARRAY_PREFIX_INT);
        IntList values = new IntList();

        do {
            this.skipWhitespace();

            this.mark(1);
            if (this.read() == ARRAY_END)
                break;
            this.reset();

            values.add(Integer.parseInt(this.readNumberAsString()));
            this.skipWhitespace();
        } while (this.read() == ENTRY_SEPARATOR);

        return values.toArray();
    }

    @Override
    public long @NotNull [] readLongArray() throws IOException {
        this.readArrayHeader(ARRAY_PREFIX_LONG);
        LongList values = new LongList();

        do {
            this.skipWhitespace();

            this.mark(1);
            if (this.read() == ARRAY_END)
                break;
            this.reset();

            values.add(Long.parseLong(this.readNumberAsString()));
            this.skipWhitespace();
        } while (this.read() == ENTRY_SEPARATOR);

        return values.toArray();
    }

    private void readArrayHeader(char typeIndicator) throws IOException {
        if (this.read() != ARRAY_START)
            throw new IOException("Invalid start of SNBT array.");

        if (this.read() != typeIndicator)
            throw new IOException("Invalid array type indicator, expected '" + typeIndicator + "'.");

        if (this.read() != ARRAY_TYPE_INDICATOR)
            throw new IOException("Invalid array type separator.");
    }

    @Override
    public @NotNull ListTag<?> readListTag(int depth) throws IOException {
        if (++depth >= 512)
            throw new NbtMaxDepthException();

        ListTag<Tag<?>> listTag = new ListTag<>();

        if (this.read() != ARRAY_START)
            throw new IOException("Invalid start of SNBT ListTag.");

        do {
            this.skipWhitespace();

            this.mark(1);
            if (this.read() == ARRAY_END)
                break;
            this.reset();

            listTag.add(this.readTag(this.peekTagId(), depth));
            this.skipWhitespace();
        } while (this.read() == ENTRY_SEPARATOR);

        return listTag;
    }

    @Override
    public @NotNull CompoundTag readCompoundTag(int depth) throws IOException {
        if (++depth >= 512)
            throw new NbtMaxDepthException();

        CompoundTag compoundTag = new CompoundTag();

        if (this.read() != COMPOUND_START)
            throw new IOException("Invalid start of SNBT CompoundTag.");

        do {
            this.skipWhitespace();

            this.mark(1);
            if (this.read() == COMPOUND_END)
                break;
            this.reset();

            String key = this.readUTF();

            this.skipWhitespace();
            if (this.read() != ENTRY_VALUE_INDICATOR)
                throw new IOException("Invalid value indicator in SNBT CompoundTag.");
            this.skipWhitespace();

            Tag<?> tag = this.readTag(this.peekTagId(), depth);
            compoundTag.put(key, tag);
            this.skipWhitespace();
        } while (this.read() == ENTRY_SEPARATOR);

        return compoundTag;
    }

    /**
     * Read a single character without increasing the index.
     */
    private int peek() throws IOException {
        this.mark(1);
        int value = this.read();
        this.reset();
        return value;
    }

    private @NotNull String readNumberAsString() throws IOException {
        String value = this.readUTF();
        int n = value.length();
        if (n == 0) return value;

        char last = value.charAt(n - 1);
        // Mirrors the prior LITERAL_SUFFIX_PATTERN [BbDdFfLlSs] character class.
        return switch (last) {
            case 'B', 'b', 'D', 'd', 'F', 'f', 'L', 'l', 'S', 's' -> value.substring(0, n - 1);
            default -> value;
        };
    }

    private @NotNull String readUTF(boolean peek) throws IOException {
        if (peek)
            this.mark(Integer.MAX_VALUE);

        final StringBuilder builder = new StringBuilder();
        final int firstChar = this.read();
        int lastChar;

        // Check if the string is quoted.
        if (firstChar == STRING_DELIMITER_1 || firstChar == STRING_DELIMITER_2) {
            // Decode escape sequences: a backslash is consumed, and the following char is appended literally.
            // This correctly unescapes the \" and \\ sequences emitted by SnbtSerializer.escapeString.
            while (true) {
                lastChar = this.read();

                if (lastChar == -1)
                    throw new IOException("Unterminated SNBT string literal.");

                if (lastChar == STRING_ESCAPE) {
                    int escaped = this.read();

                    if (escaped == -1)
                        throw new IOException("Unterminated SNBT escape sequence.");

                    builder.append((char) escaped);
                    continue;
                }

                if (lastChar == firstChar)
                    break;

                builder.append((char) lastChar);
            }
        } else {
            builder.append((char) firstChar);
            if (!peek) this.mark(1);

            while ((lastChar = this.read()) >= 0 && lastChar < 128 && IS_VALID_UNQUOTED[lastChar]) {
                builder.append((char) lastChar);
                if (!peek) this.mark(1);
            }

            if (!peek)
                this.reset();
        }

        if (peek)
            this.reset();

        String value = builder.toString();

        // Only trim whitespace if the string was NOT quoted.
        if (firstChar != STRING_DELIMITER_1 && firstChar != STRING_DELIMITER_2)
            value = value.trim();

        return value;
    }

    private byte peekTagId() throws IOException {
        return switch (this.peek()) {
            case COMPOUND_START -> TagType.COMPOUND.getId();
            case ARRAY_START -> {
                this.mark(3);
                this.read(); // Skip 1 char
                int secondChar = this.read();
                int thirdChar = this.read();
                this.reset();

                if (thirdChar == ARRAY_TYPE_INDICATOR) {
                    yield switch (secondChar) {
                        case ARRAY_PREFIX_BYTE -> TagType.BYTE_ARRAY.getId();
                        case ARRAY_PREFIX_INT -> TagType.INT_ARRAY.getId();
                        case ARRAY_PREFIX_LONG -> TagType.LONG_ARRAY.getId();
                        default -> throw new IOException("Unknown NBT array type.");
                    };
                } else
                    yield TagType.LIST.getId();
            }
            default -> {
                int firstChar = this.peek(); // Check if the value is in quotes.
                boolean isQuoted = firstChar == STRING_DELIMITER_1 || firstChar == STRING_DELIMITER_2;

                String peekString = this.readUTF(true);

                // Always use the string type for text in quotes.
                if (isQuoted)
                    yield TagType.STRING.getId();

                yield classifyNumericLiteral(peekString);
            }
        };
    }

    /**
     * Classifies an unquoted token by inspecting its trailing suffix character and validating
     * the leading numeric body in a single pass.
     *
     * <p>Replaces six sequential regex {@code .matches()} calls (one per type pattern) with a
     * trailing-char dispatch on {@code b/B/s/S/l/L/f/F/d/D} plus a fall-through scan for
     * unsuffixed integer literals. Mirrors the prior pattern semantics exactly: a token only
     * resolves to a numeric tag id when the body matches {@code [+-]?\d+} (integer suffixes) or
     * {@code [+-]?[0-9]*\.?[0-9]+} (decimal suffixes); otherwise it is a string.</p>
     *
     * @param token the unquoted token whose tag id to determine
     * @return the resolved {@link TagType} id
     */
    private static byte classifyNumericLiteral(@NotNull String token) {
        int n = token.length();
        if (n == 0)
            return TagType.STRING.getId();

        char last = token.charAt(n - 1);
        return switch (last) {
            case 'b', 'B' -> isIntegerBody(token, 0, n - 1) ? TagType.BYTE.getId() : TagType.STRING.getId();
            case 's', 'S' -> isIntegerBody(token, 0, n - 1) ? TagType.SHORT.getId() : TagType.STRING.getId();
            case 'l', 'L' -> isIntegerBody(token, 0, n - 1) ? TagType.LONG.getId() : TagType.STRING.getId();
            case 'f', 'F' -> isDecimalBody(token, 0, n - 1) ? TagType.FLOAT.getId() : TagType.STRING.getId();
            case 'd', 'D' -> isDecimalBody(token, 0, n - 1) ? TagType.DOUBLE.getId() : TagType.STRING.getId();
            default -> isIntegerBody(token, 0, n) ? TagType.INT.getId() : TagType.STRING.getId();
        };
    }

    /**
     * Returns whether {@code [start, end)} of {@code s} matches {@code [+-]?\d+}.
     */
    private static boolean isIntegerBody(@NotNull String s, int start, int end) {
        int i = start;
        if (i >= end) return false;

        char c = s.charAt(i);
        if (c == '+' || c == '-') {
            i++;
            if (i >= end) return false;
        }

        for (; i < end; i++) {
            char d = s.charAt(i);
            if (d < '0' || d > '9') return false;
        }

        return true;
    }

    /**
     * Returns whether {@code [start, end)} of {@code s} matches {@code [+-]?[0-9]*\.?[0-9]+}
     * (the body of the prior {@code FLOAT_PATTERN} / {@code DOUBLE_PATTERN}).
     */
    private static boolean isDecimalBody(@NotNull String s, int start, int end) {
        int i = start;
        if (i >= end) return false;

        char c = s.charAt(i);
        if (c == '+' || c == '-') {
            i++;
            if (i >= end) return false;
        }

        // Scan optional leading digits.
        int digitsBeforeDot = 0;
        while (i < end) {
            char d = s.charAt(i);
            if (d < '0' || d > '9') break;
            digitsBeforeDot++;
            i++;
        }

        boolean sawDot = false;
        if (i < end && s.charAt(i) == '.') {
            sawDot = true;
            i++;
        }

        // Must have at least one digit after the dot when present, or a digit before.
        int digitsAfterDot = 0;
        while (i < end) {
            char d = s.charAt(i);
            if (d < '0' || d > '9') return false;
            digitsAfterDot++;
            i++;
        }

        // Pattern requires at least one trailing digit (the regex anchors {@code [0-9]+} at the end).
        return digitsAfterDot > 0 || (!sawDot && digitsBeforeDot > 0);
    }

    /**
     * Skip over zero or more whitespace characters at the current index.
     */
    private void skipWhitespace() throws IOException {
        do {
            this.mark(1);
        } while (Character.isWhitespace(this.read()));
        this.reset();
    }

}
