package lib.minecraft.nbt.borrow;

import dev.simplified.stream.Compression;
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
 * Gold-standard parity check for the C3 borrow API.
 *
 * <p>For every fixture in the simdnbt corpus and the first 100 entries in {@code auctions.bin},
 * asserts {@code TapeParser.parse(payload).root().materialize().equals(NbtFactory.fromByteArray(payload))}.
 * Any divergence indicates a bug in one of the C3 navigator types' {@code materialize()}
 * implementations.</p>
 *
 * <p>The simdnbt corpus alone covers compound, list, all six numeric primitive kinds, all three
 * array kinds, string, and various nesting depths; the auction sample adds the long tail of
 * SkyBlock-style payloads with deeply nested compounds and item-display-style strings.</p>
 */
class BorrowParityTest {

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
    @DisplayName("simdnbt corpus fixture: borrow.materialize matches NbtFactory.fromByteArray")
    void corpusFixtureBorrowMatchesProduction(String filename) throws IOException {
        Path file = CORPUS_DIR.resolve(filename);
        assertTrue(Files.exists(file), "corpus fixture missing: " + file);

        byte[] payload = readPossiblyGzipped(file);

        CompoundTag viaProduction = NbtFactory.fromByteArray(payload);
        assertNotNull(viaProduction);

        Tape parsed = TapeParser.parse(payload);
        BorrowedCompoundTag root = parsed.root();
        CompoundTag viaBorrow = root.materialize();

        assertEquals(viaProduction, viaBorrow,
            "Borrow parity mismatch on " + filename);
    }

    @Test
    @DisplayName("first 100 auction items: borrow.materialize matches NbtFactory.fromByteArray")
    void auctionFixtureBorrowMatchesProduction() throws IOException {
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
                // decompresses, but TapeParser.parse takes raw NBT bytes only.
                byte[] raw = Compression.decompress(payload);
                Tape parsed = TapeParser.parse(raw);
                CompoundTag viaBorrow = parsed.root().materialize();

                assertEquals(viaProduction, viaBorrow,
                    "Borrow parity mismatch on auction item " + i);
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
