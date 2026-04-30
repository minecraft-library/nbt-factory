package lib.minecraft.nbt.io.json;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import lib.minecraft.nbt.exception.NbtMaxDepthException;
import lib.minecraft.nbt.io.NbtInput;
import lib.minecraft.nbt.io.util.ByteList;
import lib.minecraft.nbt.io.util.IntList;
import lib.minecraft.nbt.io.util.LongList;
import lib.minecraft.nbt.tags.Tag;
import lib.minecraft.nbt.tags.TagType;
import lib.minecraft.nbt.tags.array.ByteArrayTag;
import lib.minecraft.nbt.tags.array.IntArrayTag;
import lib.minecraft.nbt.tags.array.LongArrayTag;
import lib.minecraft.nbt.tags.collection.CompoundTag;
import lib.minecraft.nbt.tags.collection.ListTag;
import lib.minecraft.nbt.tags.primitive.ByteTag;
import lib.minecraft.nbt.tags.primitive.DoubleTag;
import lib.minecraft.nbt.tags.primitive.FloatTag;
import lib.minecraft.nbt.tags.primitive.IntTag;
import lib.minecraft.nbt.tags.primitive.LongTag;
import lib.minecraft.nbt.tags.primitive.NumericalTag;
import lib.minecraft.nbt.tags.primitive.ShortTag;
import lib.minecraft.nbt.tags.primitive.StringTag;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * NBT JSON deserialization that reads directly from a JSON reader, reconstructing tag type
 * information using the Minecraft Wiki's "Conversion from JSON" algorithm.
 *
 * <p>JSON has no native representation of NBT tag types, so types are inferred from each JSON
 * value as follows:</p>
 * <ul>
 *   <li><b>JSON string</b> - {@link StringTag}.</li>
 *   <li><b>JSON boolean</b> - {@link ByteTag} with value {@code 1} for {@code true} and
 *       {@code 0} for {@code false}.</li>
 *   <li><b>JSON null</b> - rejected with an {@link IOException}; NBT has no null representation.</li>
 *   <li><b>JSON object</b> - {@link CompoundTag}.</li>
 *   <li><b>JSON number</b> - narrowest integer tag whose range contains the value
 *       ({@link ByteTag} - {@link ShortTag} - {@link IntTag} - {@link LongTag}) when the value
 *       is integer-valued, otherwise {@link FloatTag} if the literal is representable exactly
 *       in {@code float}, else {@link DoubleTag}. The check is performed on the numeric value,
 *       not the literal's syntax, so {@code 1.0} and {@code 1.27e2} both resolve as bytes.</li>
 *   <li><b>JSON array</b> - all elements are read first, then the result type is decided from
 *       the shared element type: a homogeneous byte array becomes {@link ByteArrayTag}, a
 *       homogeneous int array becomes {@link IntArrayTag}, a homogeneous long array becomes
 *       {@link LongArrayTag}, and any other homogeneous array becomes a {@link ListTag} of that
 *       element type. An empty array becomes a {@code ListTag} with no fixed element type. A
 *       heterogeneous array is rejected with an {@code IOException}.</li>
 * </ul>
 *
 * <p><b>Round-trip caveat.</b> The wiki algorithm is inherently lossy: a JSON round-trip through
 * {@link NbtJsonSerializer} and this reader may narrow wide numeric types down to their smallest
 * fitting representation, collapse a {@code ListTag<IntTag>} of small values into a
 * {@link ByteArrayTag}, and widen floats that cannot be stored exactly in {@code float} to a
 * {@link DoubleTag}. Use the binary or SNBT backends when lossless round-trip is required.</p>
 *
 * @see NbtJsonSerializer
 * @see <a href="https://minecraft.wiki/w/NBT_format">Minecraft Wiki - NBT format - "Conversion from JSON"</a>
 */
public class NbtJsonDeserializer extends JsonReader implements NbtInput {

    public NbtJsonDeserializer(@NotNull Reader reader) {
        super(reader);
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
        Tag<?> result = this.readArrayOrTypedArray(depth);

        // Typed arrays (byte/int/long) can satisfy the wiki's array rules but are not ListTag
        // instances. Direct callers of readListTag are asserting the JSON array should map to
        // a list, so surface the mismatch rather than silently returning the wrong shape.
        if (!(result instanceof ListTag<?> list))
            throw new IOException("Expected a JSON list but parsed a typed array tag (" + result.getClass().getSimpleName() + ").");

        return list;
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
            case BEGIN_ARRAY -> this.readArrayOrTypedArray(depth);
            case NULL -> throw new IOException("Cannot convert JSON null to NBT.");
            default -> throw new IOException("Unexpected JSON token: " + token);
        };
    }

    /**
     * Maps a JSON number literal to the narrowest NBT tag per the Minecraft Wiki cascade.
     *
     * <p>The comparison runs against the numeric value, not the literal's syntax, so
     * {@code "1.0"} and {@code "1.27e2"} resolve the same way as {@code "1"} and {@code "127"}.
     * Non-integer literals fall back to {@link FloatTag} if the exact float parse equals the
     * exact double parse, otherwise {@link DoubleTag}.</p>
     *
     * <p>Plain integer literals (no decimal point, no exponent, fitting within {@code long})
     * take a fast path that bypasses the {@link BigDecimal}/{@link BigInteger} pipeline. The
     * slow path is reserved for decimal, scientific, or out-of-{@code long}-range literals.</p>
     */
    private static @NotNull Tag<?> inferNumber(@NotNull String text) throws IOException {
        // Fast path: plain integer literal (no '.', no 'e'/'E'). Length cap of 20 covers signed
        // Long.MIN_VALUE/MAX_VALUE (-9223372036854775808 .. 9223372036854775807). Any overflow or
        // unexpected formatting falls through to the BigDecimal slow path below.
        if (isPlainIntegerLiteral(text) && text.length() <= 20) {
            try {
                long l = Long.parseLong(text);
                if ((byte) l == l) return new ByteTag((byte) l);
                if ((short) l == l) return new ShortTag((short) l);
                if ((int) l == l) return new IntTag((int) l);
                return new LongTag(l);
            } catch (NumberFormatException ignored) {
                // Out-of-long-range or unexpected sign placement - fall through to BigDecimal.
            }
        }

        final BigDecimal bd;
        try {
            bd = new BigDecimal(text);
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid JSON number literal: '" + text + "'.", exception);
        }

        // Integer cascade: byte -> short -> int -> long, on the numeric value.
        try {
            BigInteger bi = bd.toBigIntegerExact();

            // bitLength() < 64 guarantees the value fits in a signed long (accounts for the sign bit).
            if (bi.bitLength() < 64) {
                long l = bi.longValueExact();
                if ((byte) l == l) return new ByteTag((byte) l);
                if ((short) l == l) return new ShortTag((short) l);
                if ((int) l == l) return new IntTag((int) l);
                return new LongTag(l);
            }
        } catch (ArithmeticException notAnInteger) {
            // Value has a non-zero fractional part - fall through to the float/double branch.
        }

        // Non-integer or out-of-long-range: prefer float iff it is exactly representable.
        double d = Double.parseDouble(text);
        float f = Float.parseFloat(text);

        if (Float.isFinite(f) && (double) f == d)
            return new FloatTag(f);

        return new DoubleTag(d);
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

    /**
     * Reads a JSON array and resolves it to the typed-array or list tag type the wiki cascade
     * produces.
     *
     * <p>The first element's inferred tag id selects the dispatch path. For the byte/int/long
     * primitive paths the elements stream directly into a primitive growable buffer
     * ({@link ByteList}/{@link IntList}/{@link LongList}), eliminating both the boxed
     * {@code ArrayList<Tag<?>>} buffer and the second narrow-conversion walk. The generic path
     * (homogeneous non-numeric, e.g. all strings or all compounds) falls back to buffering into
     * an {@code ArrayList<Tag<?>>} so the homogeneity contract is preserved.</p>
     */
    private @NotNull Tag<?> readArrayOrTypedArray(int depth) throws IOException {
        if (++depth >= 512)
            throw new NbtMaxDepthException();

        this.beginArray();

        // Empty array has no element type to key off - match the existing ListTag() convention
        // used by listsFixture().empty_list in the round-trip tests.
        if (!this.hasNext()) {
            this.endArray();
            return new ListTag<>();
        }

        Tag<?> first = this.readValue(depth);
        byte commonId = first.getId();

        // Primitive typed-array fast paths: stream directly into the matching primitive buffer.
        // (byte) / (int) Number.longValue() truncation matches the previous post-buffer cast.
        if (commonId == TagType.BYTE.getId()) {
            ByteList out = new ByteList();
            out.add(((NumericalTag<?>) first).byteValue());
            while (this.hasNext()) {
                Tag<?> element = this.readValue(depth);
                if (element.getId() != commonId)
                    throw new IOException("Heterogeneous JSON arrays cannot be converted to NBT.");
                out.add(((NumericalTag<?>) element).byteValue());
            }
            this.endArray();
            return new ByteArrayTag(out.toArray());
        }

        if (commonId == TagType.INT.getId()) {
            IntList out = new IntList();
            out.add(((NumericalTag<?>) first).intValue());
            while (this.hasNext()) {
                Tag<?> element = this.readValue(depth);
                if (element.getId() != commonId)
                    throw new IOException("Heterogeneous JSON arrays cannot be converted to NBT.");
                out.add(((NumericalTag<?>) element).intValue());
            }
            this.endArray();
            return new IntArrayTag(out.toArray());
        }

        if (commonId == TagType.LONG.getId()) {
            LongList out = new LongList();
            out.add(((NumericalTag<?>) first).longValue());
            while (this.hasNext()) {
                Tag<?> element = this.readValue(depth);
                if (element.getId() != commonId)
                    throw new IOException("Heterogeneous JSON arrays cannot be converted to NBT.");
                out.add(((NumericalTag<?>) element).longValue());
            }
            this.endArray();
            return new LongArrayTag(out.toArray());
        }

        // Generic path: homogeneous non-numeric (strings, compounds, lists, floats, doubles, shorts).
        // The heterogeneity check still requires holding every element until endArray, so a buffered
        // ArrayList remains the cheapest data structure here.
        List<Tag<?>> buffered = new ArrayList<>();
        buffered.add(first);

        while (this.hasNext()) {
            Tag<?> element = this.readValue(depth);
            if (element.getId() != commonId)
                throw new IOException("Heterogeneous JSON arrays cannot be converted to NBT.");
            buffered.add(element);
        }

        this.endArray();

        // Pre-seed the ListTag with the common element id so add() skips the isEmpty probe on
        // every entry (mirrors NbtInput.readListTag's hot path).
        @SuppressWarnings({"rawtypes", "unchecked"})
        ListTag<Tag<?>> listTag = new ListTag(commonId, buffered.size());
        listTag.addAll((List) buffered);
        return listTag;
    }

}
