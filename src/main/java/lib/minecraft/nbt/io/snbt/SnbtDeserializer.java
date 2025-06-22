package dev.sbs.minecraftapi.nbt.io.snbt;

import dev.sbs.minecraftapi.nbt.io.NbtInput;
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
import dev.sbs.api.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.StringReader;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Implementation for SNBT deserialization.
 */
public class SnbtDeserializer extends StringReader implements NbtInput {

    private static final char COMPOUND_START        = '{';
    private static final char COMPOUND_END          = '}';

    private static final char ENTRY_VALUE_INDICATOR = ':';
    private static final char ENTRY_SEPARATOR       = ',';

    private static final char ARRAY_START          = '[';
    private static final char ARRAY_END            = ']';
    private static final char ARRAY_TYPE_INDICATOR = ';';
    private static final char ARRAY_TYPE_BYTE      = 'B';
    private static final char ARRAY_TYPE_INT       = 'I';
    private static final char ARRAY_TYPE_LONG      = 'L';

    private static final char STRING_DELIMITER_1   = '\"';
    private static final char STRING_DELIMITER_2   = '\'';
    private static final char STRING_ESCAPE        = '\\';

    private static final Pattern BYTE_PATTERN      = Pattern.compile("^[+-]?\\d+b$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHORT_PATTERN     = Pattern.compile("^[+-]?\\d+s$", Pattern.CASE_INSENSITIVE);
    private static final Pattern INT_PATTERN       = Pattern.compile("^[+-]?\\d+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LONG_PATTERN      = Pattern.compile("^[+-]?\\d+l$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FLOAT_PATTERN     = Pattern.compile("^[+-]?[0-9]*\\.?[0-9]+f$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOUBLE_PATTERN    = Pattern.compile("^[+-]?[0-9]*\\.?[0-9]+d$", Pattern.CASE_INSENSITIVE);

    /**
     * Used to find and delete suffixes from numeric literals.
     */
    private static final String LITERAL_SUFFIX_PATTERN = "[BbDdFfLlSs]$";

    /**
     * All characters that can be used in strings without quotation marks (including tag names).
     */
    private static final String VALID_UNQUOTED_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-+_.";

    public SnbtDeserializer(@NotNull String snbt) {
        super(StringUtil.trimToEmpty(snbt));
    }

    @Override
    public @NotNull ByteTag readByteTag() throws IOException {
        return new ByteTag(Byte.parseByte(this.readNumberAsString()));
    }

    @Override
    public @NotNull ShortTag readShortTag() throws IOException {
        return new ShortTag(Short.parseShort(this.readNumberAsString()));
    }

    @Override
    public @NotNull IntTag readIntTag() throws IOException {
        return new IntTag(Integer.parseInt(this.readNumberAsString()));
    }

    @Override
    public @NotNull LongTag readLongTag() throws IOException {
        return new LongTag(Long.parseLong(this.readNumberAsString()));
    }

    @Override
    public @NotNull FloatTag readFloatTag() throws IOException {
        return new FloatTag(Float.parseFloat(this.readNumberAsString()));
    }

    @Override
    public @NotNull DoubleTag readDoubleTag() throws IOException {
        return new DoubleTag(Double.parseDouble(this.readNumberAsString()));
    }

    @Override
    public @NotNull ByteArrayTag readByteArrayTag() throws IOException {
        return this.readArray(ByteArrayTag::new, Byte::parseByte);
    }

    @Override
    public @NotNull StringTag readStringTag() throws IOException {
        return new StringTag(this.readString());
    }

    @Override
    public @NotNull ListTag<?> readListTag(int depth) throws IOException {
        ListTag<Tag<?>> listTag = new ListTag<>();

        if (this.read() != ARRAY_START)
            throw new IOException("Invalid start of SNBT ListTag.");

        do {
            this.skipWhitespace();

            this.mark(1);
            if (this.read() == ARRAY_END)
                break;
            this.reset();

            listTag.add(this.readTag(this.peekTagId(), this.incrementMaxDepth(depth)));
            this.skipWhitespace();
        } while (this.read() == ENTRY_SEPARATOR);

        return listTag;
    }

    @Override
    public @NotNull CompoundTag readCompoundTag(int depth) throws IOException {
        CompoundTag compoundTag = new CompoundTag();

        if (this.read() != COMPOUND_START)
            throw new IOException("Invalid start of SNBT CompoundTag.");

        do {
            this.skipWhitespace();

            this.mark(1);
            if (this.read() == COMPOUND_END)
                break;
            this.reset();

            String key = this.readString();

            this.skipWhitespace();
            if (this.read() != ENTRY_VALUE_INDICATOR)
                throw new IOException("Invalid value indicator in SNBT CompoundTag.");
            this.skipWhitespace();

            Tag<?> tag = this.readTag(this.peekTagId(), this.incrementMaxDepth(depth));
            compoundTag.put(key, tag);
            this.skipWhitespace();
        } while (this.read() == ENTRY_SEPARATOR);

        return compoundTag;
    }

    @Override
    public @NotNull IntArrayTag readIntArrayTag() throws IOException {
        return this.readArray(IntArrayTag::new, Integer::parseInt);
    }

    @Override
    public @NotNull LongArrayTag readLongArrayTag() throws IOException {
        return this.readArray(LongArrayTag::new, Long::parseLong);
    }

    /**
     * Consumes a single character.
     */
    @SuppressWarnings("all")
    private void consume() throws IOException {
        this.read();
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

    private <T extends Number, A extends ArrayTag<T>> A readArray(@NotNull Supplier<A> supplier, @NotNull Function<String, T> transformer) throws IOException {
        A arrayTag = supplier.get();

        if (this.read() != ARRAY_START)
            throw new IOException("Invalid start of SNBT ArrayTag.");

        this.consume();

        if (this.read() != ARRAY_TYPE_INDICATOR)
            throw new IOException("Invalid start of SNBT ArrayTag.");

        do {
            this.skipWhitespace();

            this.mark(1);
            if (this.read() == ARRAY_END)
                break;
            this.reset();

            arrayTag.add(transformer.apply(this.readNumberAsString()));
            this.skipWhitespace();
        } while (this.read() == ENTRY_SEPARATOR);

        return arrayTag;
    }

    private @NotNull String readNumberAsString() throws IOException {
        return this.readString().replaceFirst(LITERAL_SUFFIX_PATTERN, "");
    }

    /**
     * Read an SNBT string from the current index of a reader
     */
    private @NotNull String readString() throws IOException {
        return this.readString(false);
    }

    private @NotNull String readString(boolean peek) throws IOException {
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
                this.consume();
                int secondChar = this.read();
                int thirdChar = this.read();
                this.reset();

                if (thirdChar == ARRAY_TYPE_INDICATOR) {
                    yield switch (secondChar) {
                        case ARRAY_TYPE_BYTE -> TagType.BYTE_ARRAY.getId();
                        case ARRAY_TYPE_INT -> TagType.INT_ARRAY.getId();
                        case ARRAY_TYPE_LONG -> TagType.LONG_ARRAY.getId();
                        default -> throw new IOException("Unknown NBT array type.");
                    };
                } else
                    yield TagType.LIST.getId();
            }
            default -> {
                int firstChar = this.peek(); // Check if the value is in quotes.
                boolean isQuoted = firstChar == STRING_DELIMITER_1 || firstChar == STRING_DELIMITER_2;

                String peekString = this.readString(true);

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
