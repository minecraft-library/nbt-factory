package lib.minecraft.nbt.io.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import lib.minecraft.nbt.exception.NbtJsonException;
import lib.minecraft.nbt.exception.NbtMaxDepthException;
import lib.minecraft.nbt.io.NbtInput;
import lib.minecraft.nbt.io.util.ByteList;
import lib.minecraft.nbt.io.util.IntList;
import lib.minecraft.nbt.io.util.LongList;
import lib.minecraft.nbt.tag.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.Map;

/**
 * NBT JSON deserialization that reads directly from a JSON reader, reconstructing tag type
 * information as vanilla converts JSON to NBT.
 *
 * <p>JSON has no native representation of NBT tag types, so types are inferred from each JSON
 * value as follows:</p>
 * <ul>
 *   <li><b>JSON string</b> - {@link StringTag}.</li>
 *   <li><b>JSON boolean</b> - {@link ByteTag} with value {@code 1} for {@code true} and
 *       {@code 0} for {@code false}.</li>
 *   <li><b>JSON null</b> - rejected with an {@link NbtJsonException}; NBT has no null representation.</li>
 *   <li><b>JSON object</b> - {@link CompoundTag}.</li>
 *   <li><b>JSON number</b> - when the value is whole and fits a {@code long}, the narrowest integer
 *       tag whose range contains it ({@link ByteTag} - {@link ShortTag} - {@link IntTag} -
 *       {@link LongTag}); otherwise {@link FloatTag} when the value as a {@code double} is exactly a
 *       {@code float}, else {@link DoubleTag}. The check is performed on the numeric value, not the
 *       literal's syntax, so {@code 1.0} and {@code 1.27e2} both resolve as bytes.</li>
 *   <li><b>JSON array</b> - always a {@link ListTag}, never a typed array, whose elements may differ
 *       in type. An empty array becomes a {@code ListTag} with no element type.</li>
 * </ul>
 *
 * <p>{@link #convert(JsonElement)} applies the same rules to an already-parsed JSON tree.</p>
 *
 * <p><b>Round-trip caveat.</b> The conversion is inherently lossy: a JSON round-trip through
 * {@link NbtJsonSerializer} and this reader may narrow wide numeric types down to their smallest
 * fitting representation, turn a {@link ByteArrayTag}, {@link IntArrayTag} or {@link LongArrayTag}
 * into a {@link ListTag} of numbers, and widen floats that cannot be stored exactly in
 * {@code float} to a {@link DoubleTag}. Use the binary or SNBT backends when lossless round-trip is
 * required.</p>
 *
 * @see NbtJsonSerializer
 */
public class NbtJsonDeserializer extends JsonReader implements NbtInput {

    public NbtJsonDeserializer(@NotNull Reader reader) {
        super(reader);
    }

    /**
     * Converts an already-parsed JSON value to NBT by the rules this reader reads JSON text with.
     *
     * @param element the JSON value
     * @return the NBT value
     * @throws NbtJsonException if the value holds a JSON {@code null} or a number that does not parse
     * @throws NbtMaxDepthException if the value nests 512 levels deep or deeper
     */
    public static @NotNull Tag<?> convert(@NotNull JsonElement element) {
        return convert(element, 0);
    }

    private static @NotNull Tag<?> convert(@NotNull JsonElement element, int depth) {
        if (element.isJsonObject()) {
            if (++depth >= 512)
                throw new NbtMaxDepthException();

            JsonObject object = element.getAsJsonObject();
            CompoundTag compoundTag = new CompoundTag(object.size());

            for (Map.Entry<String, JsonElement> entry : object.entrySet())
                compoundTag.put(entry.getKey(), convert(entry.getValue(), depth));

            return compoundTag;
        }

        if (element.isJsonArray()) {
            if (++depth >= 512)
                throw new NbtMaxDepthException();

            JsonArray array = element.getAsJsonArray();
            ListTag<Tag<?>> listTag = new ListTag<>(array.size());

            for (JsonElement child : array)
                listTag.add(convert(child, depth));

            return listTag;
        }

        if (element.isJsonNull())
            throw new NbtJsonException("Cannot convert JSON null to NBT");

        JsonPrimitive primitive = element.getAsJsonPrimitive();

        if (primitive.isString())
            return new StringTag(primitive.getAsString());

        if (primitive.isBoolean())
            return new ByteTag((byte) (primitive.getAsBoolean() ? 1 : 0));

        try {
            return inferNumber(primitive.getAsBigDecimal());
        } catch (NumberFormatException exception) {
            throw new NbtJsonException(exception, "Invalid JSON number literal: '%s'", primitive.getAsString());
        }
    }

    // ------------------------------------------------------------------
    // NbtInput primitive reads
    //
    // Direct callers are rare - the main read path runs through readValue,
    // which dispatches on the peeked JsonToken instead of a type byte. These
    // implementations exist for contract completeness and for callers that
    // already know what type the next JSON value should be.
    // ------------------------------------------------------------------

    @Override
    public boolean readBoolean() throws IOException {
        return this.peek() == JsonToken.BOOLEAN ? this.nextBoolean() : this.nextLong() != 0L;
    }

    @Override
    public byte readByte() throws IOException {
        return (byte) this.nextLong();
    }

    @Override
    public short readShort() throws IOException {
        return (short) this.nextLong();
    }

    @Override
    public int readInt() throws IOException {
        return (int) this.nextLong();
    }

    @Override
    public long readLong() throws IOException {
        return this.nextLong();
    }

    @Override
    public float readFloat() throws IOException {
        return (float) this.nextDouble();
    }

    @Override
    public double readDouble() throws IOException {
        return this.nextDouble();
    }

    @Override
    public @NotNull String readUTF() throws IOException {
        return this.nextString();
    }

    @Override
    public byte @NotNull [] readByteArray() throws IOException {
        this.beginArray();
        ByteList values = new ByteList();
        while (this.hasNext())
            values.add((byte) this.nextLong());
        this.endArray();
        return values.toArray();
    }

    @Override
    public int @NotNull [] readIntArray() throws IOException {
        this.beginArray();
        IntList values = new IntList();
        while (this.hasNext())
            values.add((int) this.nextLong());
        this.endArray();
        return values.toArray();
    }

    @Override
    public long @NotNull [] readLongArray() throws IOException {
        this.beginArray();
        LongList values = new LongList();
        while (this.hasNext())
            values.add(this.nextLong());
        this.endArray();
        return values.toArray();
    }

    // ------------------------------------------------------------------
    // NbtInput dispatch overrides
    //
    // JSON has no inbound type byte, so readTag ignores the caller-supplied
    // id and inspects the next JsonToken via readValue. readCompoundTag and
    // readListTag likewise defer to readValue per child entry.
    // ------------------------------------------------------------------

    @Override
    public @NotNull Tag<?> readTag(byte id, int maxDepth) throws IOException {
        return this.readValue(maxDepth);
    }

    @Override
    public @NotNull CompoundTag readCompoundTag(int depth) throws IOException {
        if (++depth >= 512)
            throw new NbtMaxDepthException();

        CompoundTag compoundTag = new CompoundTag();
        this.beginObject();

        while (this.hasNext())
            compoundTag.put(this.nextName(), this.readValue(depth));

        this.endObject();
        return compoundTag;
    }

    @Override
    public @NotNull ListTag<?> readListTag(int depth) throws IOException {
        if (++depth >= 512)
            throw new NbtMaxDepthException();

        ListTag<Tag<?>> listTag = new ListTag<>();
        this.beginArray();

        while (this.hasNext())
            listTag.add(this.readValue(depth));

        this.endArray();
        return listTag;
    }

    // ------------------------------------------------------------------
    // Core read loop - token dispatch and type inference
    // ------------------------------------------------------------------

    private @NotNull Tag<?> readValue(int depth) throws IOException {
        JsonToken token = this.peek();

        return switch (token) {
            case STRING -> new StringTag(this.nextString());
            case BOOLEAN -> new ByteTag((byte) (this.nextBoolean() ? 1 : 0));
            case NUMBER -> inferNumber(this.nextString());
            case BEGIN_OBJECT -> this.readCompoundTag(depth);
            case BEGIN_ARRAY -> this.readListTag(depth);
            case NULL -> throw new NbtJsonException("Cannot convert JSON null to NBT");
            default -> throw new NbtJsonException("Unexpected JSON token '%s'", token);
        };
    }

    /**
     * Maps a JSON number literal to the NBT tag vanilla converts its value to.
     *
     * <p>Plain integer literals (no decimal point, no exponent, fitting within {@code long})
     * take a fast path that bypasses the {@link BigDecimal} pipeline. The slow path is reserved for
     * decimal, scientific, or out-of-{@code long}-range literals.</p>
     */
    private static @NotNull Tag<?> inferNumber(@NotNull String text) {
        // Fast path: plain integer literal (no '.', no 'e'/'E'). Length cap of 20 covers signed
        // Long.MIN_VALUE/MAX_VALUE (-9223372036854775808 .. 9223372036854775807). Any overflow or
        // unexpected formatting falls through to the BigDecimal slow path below.
        if (isPlainIntegerLiteral(text) && text.length() <= 20) {
            try {
                return narrowest(Long.parseLong(text));
            } catch (NumberFormatException ignored) {
                // Out-of-long-range or unexpected sign placement - fall through to BigDecimal.
            }
        }

        final BigDecimal value;
        try {
            value = new BigDecimal(text);
        } catch (NumberFormatException exception) {
            throw new NbtJsonException(exception, "Invalid JSON number literal: '%s'", text);
        }

        return inferNumber(value);
    }

    /**
     * Maps a JSON number to the NBT tag vanilla converts it to: the narrowest of byte, short, int and
     * long when the value is whole and fits a {@code long}, and otherwise a float when the value as a
     * {@code double} survives the narrowing to {@code float}, else a double.
     */
    private static @NotNull Tag<?> inferNumber(@NotNull BigDecimal value) {
        try {
            return narrowest(value.longValueExact());
        } catch (ArithmeticException fractionalOrTooWide) {
            double real = value.doubleValue();
            return (float) real == real ? new FloatTag((float) real) : new DoubleTag(real);
        }
    }

    /**
     * Returns the narrowest integer tag whose range holds the value.
     */
    private static @NotNull Tag<?> narrowest(long value) {
        if ((byte) value == value) return new ByteTag((byte) value);
        if ((short) value == value) return new ShortTag((short) value);
        if ((int) value == value) return new IntTag((int) value);
        return new LongTag(value);
    }

    /**
     * Returns {@code true} iff the literal contains no decimal point and no exponent marker -
     * i.e. it is a candidate for the {@link Long#parseLong(String)} fast path.
     */
    private static boolean isPlainIntegerLiteral(@NotNull String text) {
        for (int i = 0, n = text.length(); i < n; i++) {
            char c = text.charAt(i);
            if (c == '.' || c == 'e' || c == 'E') return false;
        }
        return true;
    }

}
