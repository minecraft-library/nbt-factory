package dev.sbs.minecraftapi.nbt.io.snbt;

import dev.sbs.minecraftapi.nbt.exception.NbtMaxDepthException;
import dev.sbs.minecraftapi.nbt.io.NbtInput;
import dev.sbs.minecraftapi.nbt.tags.Tag;
import dev.sbs.minecraftapi.nbt.tags.TagType;
import dev.sbs.minecraftapi.nbt.tags.collection.CompoundTag;
import dev.sbs.minecraftapi.nbt.tags.collection.ListTag;
import dev.simplified.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.StringReader;

import static dev.sbs.minecraftapi.nbt.io.snbt.SnbtConstants.*;

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
 *       {@code 34b} to {@link dev.sbs.minecraftapi.nbt.tags.primitive.ByteTag ByteTag},
 *       {@code 31415s} to {@link dev.sbs.minecraftapi.nbt.tags.primitive.ShortTag ShortTag},
 *       {@code 31415926l} to {@link dev.sbs.minecraftapi.nbt.tags.primitive.LongTag LongTag},
 *       {@code 3.14f} to {@link dev.sbs.minecraftapi.nbt.tags.primitive.FloatTag FloatTag},
 *       {@code 3.14d} to {@link dev.sbs.minecraftapi.nbt.tags.primitive.DoubleTag DoubleTag}.</li>
 *   <li><b>Numeric literal without suffix</b> - {@link dev.sbs.minecraftapi.nbt.tags.primitive.IntTag IntTag}
 *       when the literal has no decimal point,
 *       {@link dev.sbs.minecraftapi.nbt.tags.primitive.DoubleTag DoubleTag} when it does.</li>
 *   <li><b>Quoted string</b> - {@link dev.sbs.minecraftapi.nbt.tags.primitive.StringTag StringTag}
 *       always, even when the contents look numeric. Either {@code "text"} or {@code 'text'}
 *       delimiters are accepted; {@code \"}, {@code \\}, and {@code \'} escape sequences are
 *       unescaped character-for-character.</li>
 *   <li><b>Unquoted identifier</b> - classified by regex match against the numeric patterns
 *       in {@link SnbtConstants}; falls back to
 *       {@link dev.sbs.minecraftapi.nbt.tags.primitive.StringTag StringTag} on no match. Valid
 *       unquoted characters are {@code [A-Za-z0-9._+-]}.</li>
 *   <li><b>{@code [B;...]}</b> /
 *       <b>{@code [I;...]}</b> /
 *       <b>{@code [L;...]}</b> - typed arrays
 *       ({@link dev.sbs.minecraftapi.nbt.tags.array.ByteArrayTag ByteArrayTag} /
 *       {@link dev.sbs.minecraftapi.nbt.tags.array.IntArrayTag IntArrayTag} /
 *       {@link dev.sbs.minecraftapi.nbt.tags.array.LongArrayTag LongArrayTag}).</li>
 *   <li><b>{@code [value,value,...]}</b> -
 *       {@link dev.sbs.minecraftapi.nbt.tags.collection.ListTag ListTag} whose element type is
 *       decided from the first element and then enforced for the rest via
 *       {@link dev.sbs.minecraftapi.nbt.tags.collection.ListTag#add(Tag) ListTag.add}.</li>
 *   <li><b>{@code {key:value,...}}</b> -
 *       {@link dev.sbs.minecraftapi.nbt.tags.collection.CompoundTag CompoundTag}.</li>
 * </ul>
 *
 * <p>Tag-type classification for list and compound children runs through {@code peekTagId()},
 * which uses {@link StringReader#mark(int)} / {@link StringReader#reset()} lookahead to identify
 * the next value without consuming it. The depth guard and
 * {@link dev.sbs.minecraftapi.nbt.exception.NbtMaxDepthException} behaviour match the binary
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
        return LITERAL_SUFFIX_PATTERN.matcher(this.readUTF()).replaceFirst("");
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

            while (VALID_UNQUOTED_CHARS.indexOf(lastChar = this.read()) != -1) {
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

                // Try to parse the string as a numeric value.
                if (INT_PATTERN.matcher(peekString).matches())
                    yield TagType.INT.getId();
                else if (DOUBLE_PATTERN.matcher(peekString).matches())
                    yield TagType.DOUBLE.getId();
                else if (BYTE_PATTERN.matcher(peekString).matches())
                    yield TagType.BYTE.getId();
                else if (SHORT_PATTERN.matcher(peekString).matches())
                    yield TagType.SHORT.getId();
                else if (LONG_PATTERN.matcher(peekString).matches())
                    yield TagType.LONG.getId();
                else if (FLOAT_PATTERN.matcher(peekString).matches())
                    yield TagType.FLOAT.getId();
                else // Fall-back to string value.
                    yield TagType.STRING.getId();
            }
        };
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

    /**
     * Tiny growable {@code byte[]} buffer used during SNBT byte-array parsing to avoid {@code Byte} boxing.
     */
    private static final class ByteList {

        private byte[] data = new byte[16];
        private int size = 0;

        void add(byte value) {
            if (this.size == this.data.length)
                this.data = java.util.Arrays.copyOf(this.data, this.data.length << 1);

            this.data[this.size++] = value;
        }

        byte @NotNull [] toArray() {
            return java.util.Arrays.copyOf(this.data, this.size);
        }

    }

    /**
     * Tiny growable {@code int[]} buffer used during SNBT int-array parsing to avoid {@code Integer} boxing.
     */
    private static final class IntList {

        private int[] data = new int[16];
        private int size = 0;

        void add(int value) {
            if (this.size == this.data.length)
                this.data = java.util.Arrays.copyOf(this.data, this.data.length << 1);

            this.data[this.size++] = value;
        }

        int @NotNull [] toArray() {
            return java.util.Arrays.copyOf(this.data, this.size);
        }

    }

    /**
     * Tiny growable {@code long[]} buffer used during SNBT long-array parsing to avoid {@code Long} boxing.
     */
    private static final class LongList {

        private long[] data = new long[16];
        private int size = 0;

        void add(long value) {
            if (this.size == this.data.length)
                this.data = java.util.Arrays.copyOf(this.data, this.data.length << 1);

            this.data[this.size++] = value;
        }

        long @NotNull [] toArray() {
            return java.util.Arrays.copyOf(this.data, this.size);
        }

    }

}
