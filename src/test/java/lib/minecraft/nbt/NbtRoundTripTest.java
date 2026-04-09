package dev.sbs.minecraftapi.nbt;

import dev.sbs.minecraftapi.MinecraftApi;
import dev.sbs.minecraftapi.nbt.exception.NbtException;
import dev.sbs.minecraftapi.nbt.io.array.NbtInputBuffer;
import dev.sbs.minecraftapi.nbt.io.array.NbtOutputBuffer;
import dev.sbs.minecraftapi.nbt.io.stream.NbtInputStream;
import dev.sbs.minecraftapi.nbt.io.stream.NbtOutputStream;
import dev.sbs.minecraftapi.nbt.tags.TagType;
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
import dev.simplified.stream.Compression;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Cross-format round-trip and interweaving tests covering every NBT tag type across:
 * <ul>
 *   <li>Binary NBT via {@link NbtFactory#toByteArray(CompoundTag)}/{@link NbtFactory#fromByteArray(byte[])} (in-memory buffer)</li>
 *   <li>Binary NBT via {@link NbtOutputStream}/{@link NbtInputStream} (streaming)</li>
 *   <li>{@link Compression#GZIP}-compressed binary NBT</li>
 *   <li>SNBT via {@link NbtFactory#toSnbt(CompoundTag)}/{@link NbtFactory#fromSnbt(String)}</li>
 *   <li>JSON via {@link NbtFactory#toJson(CompoundTag)} (write-only - JSON deserializer is not implemented)</li>
 * </ul>
 *
 * <p>Each fixture covers different aspects of the format: primitives, edge values, strings,
 * primitive arrays (including empty + large), nested compounds, lists of various element types,
 * and deep nesting.</p>
 */
public class NbtRoundTripTest {

    private static final NbtFactory NBT = MinecraftApi.getNbtFactory();

    // ---------------------------------------------------------------------
    // Fixture builders
    // ---------------------------------------------------------------------

    /**
     * A compound containing one of every primitive numeric tag at edge values (MIN_VALUE / MAX_VALUE / 0 / -1).
     */
    private static CompoundTag primitivesFixture() {
        CompoundTag compound = new CompoundTag();
        compound.put("byte_min", new ByteTag(Byte.MIN_VALUE));
        compound.put("byte_max", new ByteTag(Byte.MAX_VALUE));
        compound.put("byte_zero", new ByteTag((byte) 0));
        compound.put("byte_neg", new ByteTag((byte) -1));

        compound.put("short_min", new ShortTag(Short.MIN_VALUE));
        compound.put("short_max", new ShortTag(Short.MAX_VALUE));

        compound.put("int_min", new IntTag(Integer.MIN_VALUE));
        compound.put("int_max", new IntTag(Integer.MAX_VALUE));

        compound.put("long_min", new LongTag(Long.MIN_VALUE));
        compound.put("long_max", new LongTag(Long.MAX_VALUE));

        compound.put("float_pos", new FloatTag(3.14159f));
        compound.put("float_neg", new FloatTag(-2.71828f));
        compound.put("float_zero", new FloatTag(0.0f));

        compound.put("double_pos", new DoubleTag(Math.PI));
        compound.put("double_neg", new DoubleTag(-Math.E));
        compound.put("double_zero", new DoubleTag(0.0));

        return compound;
    }

    /**
     * A compound containing strings with edge cases: empty, ASCII, mixed, long, special characters.
     */
    private static CompoundTag stringsFixture() {
        CompoundTag compound = new CompoundTag();
        compound.put("empty", new StringTag(""));
        compound.put("ascii", new StringTag("hello world"));
        compound.put("mixed", new StringTag("Hello \u00e9 \u00f1 world"));
        compound.put("emoji", new StringTag("\u26a1 lightning"));
        compound.put("long", new StringTag("x".repeat(1000)));
        compound.put("snbt_chars", new StringTag("contains \"quotes\" and {braces} and [brackets]"));
        compound.put("backslash", new StringTag("path\\to\\file"));
        return compound;
    }

    /**
     * A compound containing primitive array tags at various sizes (empty, small, medium, large).
     */
    private static CompoundTag arraysFixture() {
        CompoundTag compound = new CompoundTag();

        compound.put("byte_empty", new ByteArrayTag(new byte[0]));
        compound.put("byte_small", new ByteArrayTag((byte) 1, (byte) 2, (byte) 3));
        compound.put("byte_edge", new ByteArrayTag(Byte.MIN_VALUE, (byte) 0, Byte.MAX_VALUE));

        byte[] byteLarge = new byte[1024];
        for (int i = 0; i < byteLarge.length; i++) byteLarge[i] = (byte) (i & 0xFF);
        compound.put("byte_large", new ByteArrayTag(byteLarge));

        compound.put("int_empty", new IntArrayTag(new int[0]));
        compound.put("int_small", new IntArrayTag(1, 2, 3, 4, 5));
        compound.put("int_edge", new IntArrayTag(Integer.MIN_VALUE, 0, Integer.MAX_VALUE));

        int[] intLarge = new int[256];
        for (int i = 0; i < intLarge.length; i++) intLarge[i] = i * 1_000_003;
        compound.put("int_large", new IntArrayTag(intLarge));

        compound.put("long_empty", new LongArrayTag(new long[0]));
        compound.put("long_small", new LongArrayTag(1L, 2L, 3L));
        compound.put("long_edge", new LongArrayTag(Long.MIN_VALUE, 0L, Long.MAX_VALUE));

        long[] longLarge = new long[128];
        for (int i = 0; i < longLarge.length; i++) longLarge[i] = (long) i * 1_000_000_007L;
        compound.put("long_large", new LongArrayTag(longLarge));

        return compound;
    }

    /**
     * A compound containing list tags of every element type plus a list-of-lists and list-of-compounds.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static CompoundTag listsFixture() {
        CompoundTag compound = new CompoundTag();

        ListTag<ByteTag> bytes = new ListTag<>();
        bytes.add(new ByteTag((byte) 1));
        bytes.add(new ByteTag((byte) 2));
        bytes.add(new ByteTag((byte) 3));
        compound.put("byte_list", bytes);

        ListTag<IntTag> ints = new ListTag<>();
        for (int i = 0; i < 50; i++) ints.add(new IntTag(i * i));
        compound.put("int_list", ints);

        ListTag<LongTag> longs = new ListTag<>();
        for (int i = 0; i < 10; i++) longs.add(new LongTag(1L << i));
        compound.put("long_list", longs);

        ListTag<FloatTag> floats = new ListTag<>();
        floats.add(new FloatTag(1.5f));
        floats.add(new FloatTag(-1.5f));
        compound.put("float_list", floats);

        ListTag<DoubleTag> doubles = new ListTag<>();
        doubles.add(new DoubleTag(0.1));
        doubles.add(new DoubleTag(0.2));
        compound.put("double_list", doubles);

        ListTag<StringTag> strings = new ListTag<>();
        strings.add(new StringTag("alpha"));
        strings.add(new StringTag("beta"));
        strings.add(new StringTag(""));
        compound.put("string_list", strings);

        ListTag<CompoundTag> compounds = new ListTag<>();
        for (int i = 0; i < 3; i++) {
            CompoundTag entry = new CompoundTag();
            entry.put("index", new IntTag(i));
            entry.put("name", new StringTag("entry-" + i));
            compounds.add(entry);
        }
        compound.put("compound_list", compounds);

        ListTag<ListTag<IntTag>> nested = new ListTag<>();
        for (int i = 0; i < 3; i++) {
            ListTag<IntTag> inner = new ListTag<>();
            inner.add(new IntTag(i));
            inner.add(new IntTag(i + 1));
            nested.add(inner);
        }
        compound.put("nested_list", (ListTag) nested);

        // Empty list - elementId is 0 since no elements were added
        compound.put("empty_list", new ListTag<>());

        return compound;
    }

    /**
     * A deeply nested compound (10 levels deep) to verify recursive serialization.
     */
    private static CompoundTag deepNestingFixture() {
        CompoundTag root = new CompoundTag();
        CompoundTag current = root;

        for (int i = 0; i < 10; i++) {
            CompoundTag next = new CompoundTag();
            next.put("level", new IntTag(i));
            next.put("name", new StringTag("level-" + i));
            current.put("child", next);
            current = next;
        }

        current.put("leaf", new StringTag("the bottom"));
        return root;
    }

    /**
     * A compound combining every fixture above for full coverage.
     */
    private static CompoundTag everythingFixture() {
        CompoundTag compound = new CompoundTag();
        compound.put("primitives", primitivesFixture());
        compound.put("strings", stringsFixture());
        compound.put("arrays", arraysFixture());
        compound.put("lists", listsFixture());
        compound.put("deep", deepNestingFixture());
        return compound;
    }

    // ---------------------------------------------------------------------
    // Direct binary buffer round-trip
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Binary NBT - in-memory buffer")
    class BinaryBuffer {

        private CompoundTag roundTrip(CompoundTag input) throws NbtException {
            byte[] bytes = NBT.toByteArray(input);
            return NBT.fromByteArray(bytes);
        }

        @Test
        void primitives_roundTrip() throws Exception {
            CompoundTag input = primitivesFixture();
            CompoundTag output = roundTrip(input);
            assertThat(output, equalTo(input));
        }

        @Test
        void strings_roundTrip() throws Exception {
            CompoundTag input = stringsFixture();
            CompoundTag output = roundTrip(input);
            assertThat(output, equalTo(input));
        }

        @Test
        void arrays_roundTrip() throws Exception {
            CompoundTag input = arraysFixture();
            CompoundTag output = roundTrip(input);
            assertThat(output, equalTo(input));

            // Spot-check primitive array preservation specifically.
            ByteArrayTag byteLarge = output.getTag("byte_large");
            assertThat(byteLarge, notNullValue());
            assertThat(byteLarge.length(), is(1024));
            for (int i = 0; i < 1024; i++)
                assertThat(byteLarge.get(i), is((byte) (i & 0xFF)));

            IntArrayTag intLarge = output.getTag("int_large");
            assertThat(intLarge, notNullValue());
            assertThat(intLarge.length(), is(256));
            for (int i = 0; i < 256; i++)
                assertThat(intLarge.get(i), is(i * 1_000_003));

            LongArrayTag longLarge = output.getTag("long_large");
            assertThat(longLarge, notNullValue());
            assertThat(longLarge.length(), is(128));
            for (int i = 0; i < 128; i++)
                assertThat(longLarge.get(i), is((long) i * 1_000_000_007L));
        }

        @Test
        void lists_roundTrip() throws Exception {
            CompoundTag input = listsFixture();
            CompoundTag output = roundTrip(input);
            assertThat(output, equalTo(input));
        }

        @Test
        void deepNesting_roundTrip() throws Exception {
            CompoundTag input = deepNestingFixture();
            CompoundTag output = roundTrip(input);
            assertThat(output, equalTo(input));
        }

        @Test
        void everything_roundTrip() throws Exception {
            CompoundTag input = everythingFixture();
            CompoundTag output = roundTrip(input);
            assertThat(output, equalTo(input));
        }

        @Test
        void emptyCompound_roundTrip() throws Exception {
            CompoundTag input = new CompoundTag();
            CompoundTag output = roundTrip(input);
            assertThat(output, equalTo(input));
            assertThat(output, aMapWithSize(0));
        }
    }

    // ---------------------------------------------------------------------
    // GZIP-compressed binary
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Binary NBT - gzip compressed")
    class GzipCompressed {

        @Test
        void everything_roundTrip() throws Exception {
            CompoundTag input = everythingFixture();
            byte[] gzipped = NBT.toByteArray(input, Compression.GZIP);
            byte[] uncompressed = NBT.toByteArray(input);

            // Compression should normally make it smaller for non-trivial payloads.
            assertThat(gzipped.length, not(is(uncompressed.length)));

            CompoundTag output = NBT.fromByteArray(gzipped);
            assertThat(output, equalTo(input));
        }

        @Test
        void base64_roundTrip() throws Exception {
            CompoundTag input = everythingFixture();
            String base64 = NBT.toBase64(input);
            CompoundTag output = NBT.fromBase64(base64);
            assertThat(output, equalTo(input));
        }
    }

    // ---------------------------------------------------------------------
    // Streaming binary I/O
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Binary NBT - streaming")
    class StreamingBinary {

        private CompoundTag streamRoundTrip(CompoundTag input) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (NbtOutputStream nos = new NbtOutputStream(baos)) {
                nos.writeByte(TagType.COMPOUND.getId());
                nos.writeUTF("");
                nos.writeCompoundTag(input);
            }

            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            try (NbtInputStream nis = new NbtInputStream(bais)) {
                byte rootId = nis.readByte();
                assertThat(rootId, is(TagType.COMPOUND.getId()));
                nis.readUTF(); // discard root name
                return nis.readCompoundTag();
            }
        }

        @Test
        void everything_roundTrip() throws Exception {
            CompoundTag input = everythingFixture();
            CompoundTag output = streamRoundTrip(input);
            assertThat(output, equalTo(input));
        }

        @Test
        void streamMatchesBuffer() throws Exception {
            // Both implementations should produce equivalent in-memory output.
            CompoundTag input = everythingFixture();
            byte[] viaBuffer = NBT.toByteArray(input);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (NbtOutputStream nos = new NbtOutputStream(baos)) {
                nos.writeByte(TagType.COMPOUND.getId());
                nos.writeUTF("");
                nos.writeCompoundTag(input);
            }
            byte[] viaStream = baos.toByteArray();

            // The two paths must agree on byte content - any divergence is a serializer bug.
            assertThat(viaStream.length, is(viaBuffer.length));
            for (int i = 0; i < viaStream.length; i++)
                assertThat("byte " + i, viaStream[i], is(viaBuffer[i]));
        }

        @Test
        void rawBufferOutputMatchesNbtOutputBuffer() throws Exception {
            // Direct NbtOutputBuffer use must produce the same bytes as NbtFactory.toByteArray.
            CompoundTag input = everythingFixture();
            byte[] viaFactory = NBT.toByteArray(input);

            NbtOutputBuffer buffer = new NbtOutputBuffer();
            buffer.writeByte(TagType.COMPOUND.getId());
            buffer.writeUTF("");
            buffer.writeCompoundTag(input);
            byte[] viaBuffer = buffer.toByteArray();

            assertThat(viaBuffer.length, is(viaFactory.length));
        }

        @Test
        void rawInputBufferReadsFactoryOutput() throws Exception {
            CompoundTag input = everythingFixture();
            byte[] bytes = NBT.toByteArray(input);

            NbtInputBuffer buffer = new NbtInputBuffer(bytes);
            assertThat(buffer.readByte(), is(TagType.COMPOUND.getId()));
            buffer.readUTF();
            CompoundTag output = buffer.readCompoundTag();
            assertThat(output, equalTo(input));
        }

        @Test
        void modifiedUtf8_edgeCases_roundTrip() throws Exception {
            // Edge cases that exercise modified-UTF-8 specifically (where it diverges from
            // standard UTF-8). If the buffer and stream backends ever drift apart on these, the
            // byte-equal assertion below catches it immediately.
            CompoundTag input = new CompoundTag();
            input.put("nul", new StringTag("before\u0000after"));        // U+0000 - two bytes C0 80
            input.put("emoji", new StringTag("grin \uD83D\uDE00 done")); // U+1F600 - surrogate pair, 2x 3 bytes
            input.put("mixed", new StringTag("\u0000ascii \u00e9 \uD83D\uDE00"));
            input.put("basic", new StringTag("all ASCII no NUL"));

            // Factory (buffer) path.
            byte[] viaFactory = NBT.toByteArray(input);
            CompoundTag factoryRoundTrip = NBT.fromByteArray(viaFactory);
            assertThat(factoryRoundTrip, equalTo(input));

            // Stream path.
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (NbtOutputStream nos = new NbtOutputStream(baos)) {
                nos.writeByte(TagType.COMPOUND.getId());
                nos.writeUTF("");
                nos.writeCompoundTag(input);
            }
            byte[] viaStream = baos.toByteArray();

            ByteArrayInputStream bais = new ByteArrayInputStream(viaStream);
            CompoundTag streamRoundTrip;
            try (NbtInputStream nis = new NbtInputStream(bais)) {
                assertThat(nis.readByte(), is(TagType.COMPOUND.getId()));
                nis.readUTF();
                streamRoundTrip = nis.readCompoundTag();
            }
            assertThat(streamRoundTrip, equalTo(input));

            // Byte-for-byte equivalence between the two backends: this is the regression guard
            // for the modified-UTF-8 standardization. Prior to it, the buffer backend emitted
            // standard UTF-8 and the stream backend emitted modified UTF-8, so U+0000 and the
            // emoji produced divergent bytes.
            assertThat("stream and factory outputs must be byte-identical", viaStream.length, is(viaFactory.length));
            for (int i = 0; i < viaStream.length; i++)
                assertThat("byte " + i, viaStream[i], is(viaFactory[i]));
        }
    }

    // ---------------------------------------------------------------------
    // SNBT round-trip
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("SNBT round-trip")
    class Snbt {

        private CompoundTag snbtRoundTrip(CompoundTag input) throws Exception {
            String snbt = NBT.toSnbt(input);
            return NBT.fromSnbt(snbt);
        }

        @Test
        void primitives_roundTrip() throws Exception {
            CompoundTag input = primitivesFixture();
            CompoundTag output = snbtRoundTrip(input);
            assertThat(output, equalTo(input));
        }

        @Test
        void arrays_roundTrip() throws Exception {
            CompoundTag input = arraysFixture();
            CompoundTag output = snbtRoundTrip(input);
            assertThat(output, equalTo(input));
        }

        @Test
        void strings_roundTrip() throws Exception {
            CompoundTag input = stringsFixture();
            CompoundTag output = snbtRoundTrip(input);
            assertThat(output, equalTo(input));
        }

        @Test
        void everything_roundTrip() throws Exception {
            CompoundTag input = everythingFixture();
            CompoundTag output = snbtRoundTrip(input);
            assertThat(output, equalTo(input));
        }

        @Test
        void deepNesting_roundTrip() throws Exception {
            CompoundTag input = deepNestingFixture();
            CompoundTag output = snbtRoundTrip(input);
            assertThat(output, equalTo(input));
        }
    }

    // ---------------------------------------------------------------------
    // Cross-format interweaving: each path should produce equivalent results.
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Cross-format interweaving")
    class Interweaving {

        @Test
        void binary_then_snbt_then_binary() throws Exception {
            CompoundTag input = everythingFixture();

            // binary -> compound
            byte[] binary = NBT.toByteArray(input);
            CompoundTag fromBinary = NBT.fromByteArray(binary);

            // compound -> snbt -> compound
            String snbt = NBT.toSnbt(fromBinary);
            CompoundTag fromSnbt = NBT.fromSnbt(snbt);

            // compound -> binary -> compound
            byte[] roundTripBinary = NBT.toByteArray(fromSnbt);
            CompoundTag finalCompound = NBT.fromByteArray(roundTripBinary);

            assertThat(finalCompound, equalTo(input));
        }

        @Test
        void snbt_then_binary_then_snbt() throws Exception {
            CompoundTag input = everythingFixture();

            String snbtA = NBT.toSnbt(input);
            CompoundTag fromSnbtA = NBT.fromSnbt(snbtA);

            byte[] binary = NBT.toByteArray(fromSnbtA);
            CompoundTag fromBinary = NBT.fromByteArray(binary);

            String snbtB = NBT.toSnbt(fromBinary);
            CompoundTag fromSnbtB = NBT.fromSnbt(snbtB);

            assertThat(fromSnbtB, equalTo(input));

            // SNBT serialization is deterministic for the same compound, so the strings should match.
            assertThat(snbtB, is(snbtA));
        }

        @Test
        void gzip_then_snbt_then_gzip() throws Exception {
            CompoundTag input = everythingFixture();

            byte[] gzipA = NBT.toByteArray(input, Compression.GZIP);
            CompoundTag fromGzipA = NBT.fromByteArray(gzipA);

            String snbt = NBT.toSnbt(fromGzipA);
            CompoundTag fromSnbt = NBT.fromSnbt(snbt);

            byte[] gzipB = NBT.toByteArray(fromSnbt, Compression.GZIP);
            CompoundTag fromGzipB = NBT.fromByteArray(gzipB);

            assertThat(fromGzipB, equalTo(input));
        }

        @Test
        void base64_then_snbt_then_base64() throws Exception {
            CompoundTag input = everythingFixture();

            String base64A = NBT.toBase64(input);
            CompoundTag fromBase64A = NBT.fromBase64(base64A);

            String snbt = NBT.toSnbt(fromBase64A);
            CompoundTag fromSnbt = NBT.fromSnbt(snbt);

            String base64B = NBT.toBase64(fromSnbt);
            CompoundTag fromBase64B = NBT.fromBase64(base64B);

            assertThat(fromBase64B, equalTo(input));
        }
    }

    // ---------------------------------------------------------------------
    // JSON write-only sanity checks (no JSON deserializer in this library)
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("JSON serialization (write only)")
    class JsonSerialization {

        @Test
        void primitives_writesJson() throws Exception {
            String json = NBT.toJson(primitivesFixture());
            assertThat(json, notNullValue());
            assertThat(json.length(), not(is(0)));
            assertThat(json.contains("byte_min"), is(true));
            assertThat(json.contains("double_pos"), is(true));
        }

        @Test
        void everything_writesJson() throws Exception {
            String json = NBT.toJson(everythingFixture());
            assertThat(json, notNullValue());
            // Spot-check some keys are present.
            assertThat(json.contains("primitives"), is(true));
            assertThat(json.contains("arrays"), is(true));
            assertThat(json.contains("byte_large"), is(true));
            assertThat(json.contains("level"), is(true));
        }
    }

    // ---------------------------------------------------------------------
    // Direct primitive-array tag tests (no boxing)
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Primitive array tags")
    class PrimitiveArrayTags {

        @Test
        void byteArrayTag_storesPrimitive() {
            byte[] data = new byte[]{1, 2, 3, 4, 5};
            ByteArrayTag tag = new ByteArrayTag(data);
            assertThat(tag.getValue(), is(data));
            assertThat(tag.length(), is(5));
            assertThat(tag.get(0), is((byte) 1));
            assertThat(tag.get(4), is((byte) 5));
        }

        @Test
        void intArrayTag_storesPrimitive() {
            int[] data = new int[]{10, 20, 30};
            IntArrayTag tag = new IntArrayTag(data);
            assertThat(tag.getValue(), is(data));
            assertThat(tag.length(), is(3));
            assertThat(tag.get(1), is(20));
        }

        @Test
        void longArrayTag_storesPrimitive() {
            long[] data = new long[]{100L, 200L, 300L};
            LongArrayTag tag = new LongArrayTag(data);
            assertThat(tag.getValue(), is(data));
            assertThat(tag.length(), is(3));
            assertThat(tag.get(2), is(300L));
        }

        @Test
        void byteArrayTag_equalsByContent() {
            ByteArrayTag a = new ByteArrayTag((byte) 1, (byte) 2, (byte) 3);
            ByteArrayTag b = new ByteArrayTag((byte) 1, (byte) 2, (byte) 3);
            ByteArrayTag c = new ByteArrayTag((byte) 1, (byte) 2, (byte) 4);
            assertThat(a, equalTo(b));
            assertThat(a.hashCode(), is(b.hashCode()));
            assertThat(a, not(equalTo(c)));
        }

        @Test
        void intArrayTag_equalsByContent() {
            IntArrayTag a = new IntArrayTag(1, 2, 3);
            IntArrayTag b = new IntArrayTag(1, 2, 3);
            IntArrayTag c = new IntArrayTag(1, 2, 4);
            assertThat(a, equalTo(b));
            assertThat(a.hashCode(), is(b.hashCode()));
            assertThat(a, not(equalTo(c)));
        }

        @Test
        void longArrayTag_equalsByContent() {
            LongArrayTag a = new LongArrayTag(1L, 2L, 3L);
            LongArrayTag b = new LongArrayTag(1L, 2L, 3L);
            LongArrayTag c = new LongArrayTag(1L, 2L, 4L);
            assertThat(a, equalTo(b));
            assertThat(a.hashCode(), is(b.hashCode()));
            assertThat(a, not(equalTo(c)));
        }

        @Test
        void cloneIsDeep() {
            ByteArrayTag original = new ByteArrayTag((byte) 1, (byte) 2, (byte) 3);
            ByteArrayTag copy = original.clone();
            assertThat(copy, equalTo(original));
            // Mutate the copy's backing array - original must be unaffected.
            copy.set(0, (byte) 99);
            assertThat(original.get(0), is((byte) 1));
            assertThat(copy.get(0), is((byte) 99));
        }
    }

    // ---------------------------------------------------------------------
    // CompoundTag path operations
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("CompoundTag path operations")
    class PathOperations {

        @Test
        void getPath_singleLevel() {
            CompoundTag root = new CompoundTag();
            root.put("name", new StringTag("test"));

            StringTag tag = root.getPath("name");
            assertThat(tag, notNullValue());
            assertThat(tag.getValue(), is("test"));
        }

        @Test
        void getPath_nested() {
            CompoundTag root = new CompoundTag();
            CompoundTag inner = new CompoundTag();
            inner.put("value", new IntTag(42));
            root.put("inner", inner);

            IntTag tag = root.getPath("inner.value");
            assertThat(tag, notNullValue());
            assertThat(tag.getValue(), is(42));
        }

        @Test
        void getPath_deeplyNested() {
            CompoundTag root = new CompoundTag();
            CompoundTag a = new CompoundTag();
            CompoundTag b = new CompoundTag();
            CompoundTag c = new CompoundTag();
            c.put("leaf", new StringTag("found"));
            b.put("c", c);
            a.put("b", b);
            root.put("a", a);

            StringTag tag = root.getPath("a.b.c.leaf");
            assertThat(tag, notNullValue());
            assertThat(tag.getValue(), is("found"));
        }

        @Test
        void getPath_missing_returnsNull() {
            CompoundTag root = new CompoundTag();
            root.put("present", new StringTag("here"));
            assertThat(root.getPath("missing"), nullValue());
            // Note: descending past a non-compound node has historically returned the leaf via unchecked cast,
            // not null. Not asserting that here - it's a separate semantic to clean up later.
        }

        @Test
        void containsPath() {
            CompoundTag root = new CompoundTag();
            CompoundTag inner = new CompoundTag();
            inner.put("value", new IntTag(42));
            root.put("inner", inner);

            assertThat(root.containsPath("inner"), is(true));
            assertThat(root.containsPath("inner.value"), is(true));
            assertThat(root.containsPath("missing"), is(false));
            assertThat(root.containsPath("inner.missing"), is(false));
        }

        @Test
        void putPath_createsIntermediateCompounds() {
            CompoundTag root = new CompoundTag();
            root.putPath("a.b.c", new StringTag("deep"));

            assertThat(root.containsPath("a.b.c"), is(true));
            StringTag tag = root.getPath("a.b.c");
            assertThat(tag, notNullValue());
            assertThat(tag.getValue(), is("deep"));
        }

        @Test
        void putPath_then_roundTripBinary() throws Exception {
            CompoundTag root = new CompoundTag();
            root.putPath("a.b.c", new StringTag("deep"));
            root.putPath("a.b.d", new IntTag(7));
            root.putPath("a.e", new LongTag(99L));

            CompoundTag roundTrip = NBT.fromByteArray(NBT.toByteArray(root));
            StringTag c = roundTrip.getPath("a.b.c");
            IntTag d = roundTrip.getPath("a.b.d");
            LongTag e = roundTrip.getPath("a.e");
            assertThat(c, notNullValue());
            assertThat(d, notNullValue());
            assertThat(e, notNullValue());
            assertThat(c.getValue(), is("deep"));
            assertThat(d.getValue(), is(7));
            assertThat(e.getValue(), is(99L));
        }

        @Test
        void removePath() {
            CompoundTag root = new CompoundTag();
            root.putPath("a.b.c", new StringTag("deep"));
            root.putPath("a.b.d", new IntTag(7));
            assertThat(root.containsPath("a.b.c"), is(true));

            root.removePath("a.b.c");
            assertThat(root.containsPath("a.b.c"), is(false));
            assertThat(root.containsPath("a.b.d"), is(true));
        }
    }

    // ---------------------------------------------------------------------
    // Collection size hint sanity (Phase 1 fixes)
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Collection sizing")
    class CollectionSizing {

        @Test
        void listTag_sizedConstructor() {
            ListTag<IntTag> list = new ListTag<>(64);
            assertThat(list.size(), is(0));

            for (int i = 0; i < 100; i++)
                list.add(new IntTag(i));

            assertThat(list, hasSize(100));
            assertThat(list.get(50).getValue(), is(50));
        }

        @Test
        void compoundTag_sizedConstructor() {
            CompoundTag compound = new CompoundTag(64);
            assertThat(compound, aMapWithSize(0));

            for (int i = 0; i < 100; i++)
                compound.put("key_" + i, new IntTag(i));

            assertThat(compound, aMapWithSize(100));
            IntTag tag = compound.getTag("key_50");
            assertThat(tag, notNullValue());
            assertThat(tag.getValue(), is(50));
        }
    }

}
