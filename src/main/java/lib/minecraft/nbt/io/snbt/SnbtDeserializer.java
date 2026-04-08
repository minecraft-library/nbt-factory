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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;

import static dev.sbs.minecraftapi.nbt.io.snbt.SnbtUtil.*;

/**
 * Implementation for SNBT deserialization.
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
    public @NotNull Byte[] readByteArray() throws IOException {
        return this.readArray(ARRAY_PREFIX_BYTE, Byte::parseByte, Byte[]::new);
    }

    @Override
    public @NotNull Integer[] readIntArray() throws IOException {
        return this.readArray(ARRAY_PREFIX_INT, Integer::parseInt, Integer[]::new);
    }

    @Override
    public @NotNull Long[] readLongArray() throws IOException {
        return this.readArray(ARRAY_PREFIX_LONG, Long::parseLong, Long[]::new);
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

    /**
     * Generic reader for SNBT type-tagged arrays.
     *
     * @param typeIndicator the expected type indicator character (B, I, L)
     * @param transformer function to transform string into the numeric type
     * @param arrayFactory factory producing the output array of the given size
     * @param <T> the numeric wrapper type (Byte, Integer, Long)
     * @return an array of the specified wrapper type containing the parsed values
     */
    private <T extends Number> @NotNull T[] readArray(char typeIndicator, @NotNull Function<String, T> transformer, @NotNull IntFunction<T[]> arrayFactory) throws IOException {
        if (this.read() != ARRAY_START)
            throw new IOException("Invalid start of SNBT array.");

        if (this.read() != typeIndicator)
            throw new IOException("Invalid array type indicator, expected '" + typeIndicator + "'.");

        if (this.read() != ARRAY_TYPE_INDICATOR)
            throw new IOException("Invalid array type separator.");

        List<T> values = new ArrayList<>();

        do {
            this.skipWhitespace();

            this.mark(1);
            if (this.read() == ARRAY_END)
                break;
            this.reset();

            values.add(transformer.apply(this.readNumberAsString()));
            this.skipWhitespace();
        } while (this.read() == ENTRY_SEPARATOR);

        return values.toArray(arrayFactory);
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
            boolean isEscaped = false;

            while ((lastChar = this.read()) != firstChar || isEscaped) {
                builder.append((char) lastChar);
                isEscaped = lastChar == STRING_ESCAPE;
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

}
