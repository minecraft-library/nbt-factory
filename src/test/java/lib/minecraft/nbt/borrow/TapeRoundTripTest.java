package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.NbtFactory;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip parity for {@link Tape#encode(CompoundTag)} + {@link Tape#materialize()} against
 * the simdnbt corpus and a short slice of the auction fixture.
 *
 * <p>Encoding produces a tape from a materialized compound; materializing walks the tape and
 * reconstructs an equivalent compound. The pair must round-trip for every input the production
 * codec accepts. This test plus {@link TapeShapeTest} pin every contract C2's streaming parser
 * needs to honor when it replaces the encoder body.</p>
 */
class TapeRoundTripTest {

    private static final Path CORPUS_DIR = Paths.get("src/test/resources/simdnbt-corpus");

    private static final Path AUCTION_FIXTURE = Paths.get("src/test/resources/nbt-bench-fixture/auctions.bin");

    private static final int AUCTION_SAMPLE_LIMIT = 10;

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
        "complex_player.dat",
        "hypixel.nbt",
        "level.dat",
        "bigtest.nbt",
        "simple_player.dat",
        "inttest1023.nbt"
    })
    @DisplayName("simdnbt corpus fixture round-trips through Tape.encode + Tape.materialize")
    void corpusFileRoundTrips(String filename) throws IOException {
        Path file = CORPUS_DIR.resolve(filename);
        assertTrue(Files.exists(file), "corpus fixture missing: " + file);

        byte[] payload = readPossiblyGzipped(file);
        CompoundTag original = NbtFactory.fromByteArray(payload);
        assertNotNull(original);
        assertTrue(original.size() > 0, "decoded compound was empty: " + filename);

        Tape tape = Tape.encode(original);
        assertTrue(tape.tapeSize() >= 2, "tape too short for non-empty compound: " + filename);

        CompoundTag materialized = tape.materialize();
        assertEquals(original, materialized, "tape round-trip mismatch on " + filename);
    }

    @Test
    @DisplayName("first 10 auction items round-trip through Tape.encode + Tape.materialize")
    void auctionFixtureRoundTrips() throws IOException {
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
                CompoundTag original = NbtFactory.fromByteArray(payload);
                Tape tape = Tape.encode(original);
                CompoundTag materialized = tape.materialize();
                assertEquals(original, materialized, "auction item " + i + " did not round-trip");
            }
        }
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
