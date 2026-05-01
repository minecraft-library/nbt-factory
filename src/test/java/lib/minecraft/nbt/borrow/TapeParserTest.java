package lib.minecraft.nbt.borrow;

import dev.simplified.stream.Compression;
import lib.minecraft.nbt.NbtFactory;
import lib.minecraft.nbt.exception.NbtException;
import lib.minecraft.nbt.exception.NbtMaxDepthException;
import lib.minecraft.nbt.tags.collection.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins parity between {@link TapeParser#parse(byte[])} and {@link NbtFactory#fromByteArray(byte[])}
 * on every fixture in the simdnbt corpus and a sample of the auction fixture, plus exercises the
 * adversarial-input failure modes (depth cap, truncated buffer, bad type id).
 *
 * <p>For each fixture the test materializes the binary in two ways:</p>
 * <ol>
 *   <li>{@link NbtFactory#fromByteArray(byte[])} - the production materializing path.</li>
 *   <li>{@link TapeParser#parse(byte[])} followed by {@link Tape#materialize()} - the new
 *       streaming-parser path.</li>
 * </ol>
 *
 * <p>The two compounds must compare equal. This is the gold-standard parity check between C2's
 * parser and the existing path; any divergence here breaks the contract C3's borrowed navigators
 * depend on.</p>
 */
class TapeParserTest {

    private static final Path CORPUS_DIR = Paths.get("src/test/resources/simdnbt-corpus");

    private static final Path AUCTION_FIXTURE = Paths.get("src/test/resources/nbt-bench-fixture/auctions.bin");

    private static final int AUCTION_SAMPLE_LIMIT = 100;

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
        "complex_player.dat",
        "hypixel.nbt",
        "level.dat",
        "bigtest.nbt",
        "simple_player.dat",
        "inttest1023.nbt"
    })
    @DisplayName("simdnbt corpus fixture: TapeParser.parse + materialize matches NbtFactory.fromByteArray")
    void corpusFixtureMatchesProduction(String filename) throws IOException {
        Path file = CORPUS_DIR.resolve(filename);
        assertTrue(Files.exists(file), "corpus fixture missing: " + file);

        byte[] payload = readPossiblyGzipped(file);

        CompoundTag viaProduction = NbtFactory.fromByteArray(payload);
        assertNotNull(viaProduction);

        Tape parsed = TapeParser.parse(payload);
        assertTrue(parsed.tapeSize() >= 2, "tape too short for non-empty compound: " + filename);

        CompoundTag viaTape = parsed.materialize();
        assertEquals(viaProduction, viaTape, "TapeParser parity mismatch on " + filename);
    }

    @Test
    @DisplayName("first 100 auction items: TapeParser.parse + materialize matches NbtFactory.fromByteArray")
    void auctionFixtureMatchesProduction() throws IOException {
        if (!Files.exists(AUCTION_FIXTURE))
            // Auction fixture is generated on demand; absence is not a test failure (mirrors the
            // contract documented in CLAUDE.md - run ./gradlew generateAuctionFixture).
            return;

        try (DataInputStream in = new DataInputStream(Files.newInputStream(AUCTION_FIXTURE))) {
            int total = in.readInt();
            int sample = Math.min(total, AUCTION_SAMPLE_LIMIT);

            for (int i = 0; i < sample; i++) {
                int len = in.readInt();
                byte[] payload = in.readNBytes(len);

                CompoundTag viaProduction = NbtFactory.fromByteArray(payload);
                // Auction items ship gzipped on the wire; NbtFactory.fromByteArray transparently
                // decompresses, but TapeParser.parse takes raw NBT bytes only (the C5 entry point
                // will own the decompression decision; here we feed the parser what it expects).
                byte[] raw = Compression.decompress(payload);
                Tape parsed = TapeParser.parse(raw);
                CompoundTag viaTape = parsed.materialize();

                assertEquals(viaProduction, viaTape, "TapeParser parity mismatch on auction item " + i);
            }
        }
    }

    @Test
    @DisplayName("600-level deep compound throws NbtMaxDepthException, not StackOverflowError")
    void deepNestingTripsDepthCap() throws IOException {
        // Hand-craft a payload with 600 nested compounds. Wire layout per level:
        //   <type:0x0A><namelen:0x00 0x01>'a'   (5 bytes opening a named compound entry)
        // Root preamble: 0x0A 0x00 0x00 (compound + empty root name)
        // Then 600 levels of "open named child compound" + 600 TAG_End bytes + outer TAG_End.
        int depth = 600;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(10); // root TAG_Compound
        out.write(0); // root name len high
        out.write(0); // root name len low

        for (int i = 0; i < depth; i++) {
            out.write(10);            // entry type: TAG_Compound
            out.write(0); out.write(1); // name length 1
            out.write('a');           // name 'a'
        }

        for (int i = 0; i < depth; i++)
            out.write(0); // TAG_End closes innermost compound

        out.write(0); // TAG_End closes root
        byte[] payload = out.toByteArray();

        assertThrows(NbtMaxDepthException.class, () -> TapeParser.parse(payload),
            "parser must reject 600-level nesting with NbtMaxDepthException");
    }

    @Test
    @DisplayName("truncated buffer surfaces a clear NbtException")
    void truncatedBufferRejected() {
        // Just a TAG_Compound type byte with no name length - parser should fail on the readUnsignedShort
        // in parseRoot when it tries to read the 2-byte root name length.
        byte[] payload = new byte[]{10};

        assertThrows(NbtException.class, () -> TapeParser.parse(payload),
            "parser must reject truncated input");
    }

    @Test
    @DisplayName("non-compound root id surfaces a clear NbtException")
    void badRootIdRejected() {
        // Root type 0x42 is not TAG_Compound (10).
        byte[] payload = new byte[]{0x42, 0x00, 0x00};

        assertThrows(NbtException.class, () -> TapeParser.parse(payload),
            "parser must reject non-compound root type id");
    }

    @Test
    @DisplayName("unknown tag id inside compound surfaces a clear NbtException")
    void badInnerTagIdRejected() {
        // Root compound + entry with unknown type id 99.
        // Layout: 0x0A 0x00 0x00 [0x63 0x00 0x01 'a' ...] - the inner type id 0x63 (99) is invalid.
        byte[] payload = new byte[]{0x0A, 0x00, 0x00, 0x63, 0x00, 0x01, 'a', 0x00};

        assertThrows(NbtException.class, () -> TapeParser.parse(payload),
            "parser must reject unknown tag id inside compound");
    }

    private static byte[] readPossiblyGzipped(Path file) throws IOException {
        byte[] raw = Files.readAllBytes(file);

        if (raw.length >= 2 && (raw[0] & 0xff) == 0x1f && (raw[1] & 0xff) == 0x8b) {
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(raw));
                 ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length * 4)) {
                in.transferTo(out);
                return out.toByteArray();
            }
        }

        return raw;
    }

}
