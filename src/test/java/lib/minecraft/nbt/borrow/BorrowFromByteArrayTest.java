package lib.minecraft.nbt.borrow;

import dev.simplified.stream.Compression;
import lib.minecraft.nbt.NbtFactory;
import lib.minecraft.nbt.exception.NbtException;
import lib.minecraft.nbt.tags.collection.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link NbtFactory#borrowFromByteArray(byte[])} parity against
 * {@link NbtFactory#fromByteArray(byte[])} on every fixture in the simdnbt corpus, exercises the
 * gzip auto-detect path on the {@code .dat}-style fixtures, and verifies the buffer-retention
 * contract documented on the entry point: a borrowed tree continues to materialize correctly
 * after the original input array reference is dropped and a GC is forced.
 *
 * <p>This is the gold-standard parity check for the public Phase C5 entry point - any divergence
 * indicates a bug in the gzip-detect path or in how the entry point hands its decompressed buffer
 * to {@link TapeParser}.</p>
 */
class BorrowFromByteArrayTest {

    private static final Path CORPUS_DIR = Paths.get("src/test/resources/simdnbt-corpus");

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
        "complex_player.dat",
        "hypixel.nbt",
        "level.dat",
        "bigtest.nbt",
        "simple_player.dat",
        "inttest1023.nbt",
        "hello_world.nbt"
    })
    @DisplayName("borrowFromByteArray.materialize matches fromByteArray on every corpus fixture")
    void corpusFixtureBorrowMatchesProduction(String filename) throws IOException {
        Path file = CORPUS_DIR.resolve(filename);
        assertTrue(Files.exists(file), "corpus fixture missing: " + file);

        byte[] payload = Files.readAllBytes(file);

        CompoundTag viaProduction = NbtFactory.fromByteArray(payload);
        assertNotNull(viaProduction);

        BorrowedCompoundTag borrowed = NbtFactory.borrowFromByteArray(payload);
        CompoundTag viaBorrow = borrowed.materialize();

        assertEquals(viaProduction, viaBorrow,
            "borrowFromByteArray parity mismatch on " + filename);
    }

    @Test
    @DisplayName("explicitly synthesized gzipped payload round-trips through borrowFromByteArray")
    void synthesizedGzipPayloadRoundTrips() throws IOException {
        // Take a known-raw fixture, compress it explicitly, and assert the entry point auto-detects
        // gzip and produces the same materialized tree as the raw path. Belt-and-suspenders against
        // the parametric test above (whose corpus already includes four gzipped .dat fixtures).
        byte[] raw = Files.readAllBytes(CORPUS_DIR.resolve("hypixel.nbt"));
        byte[] gzipped = Compression.compress(raw, 0, raw.length, Compression.GZIP);

        CompoundTag viaProductionRaw = NbtFactory.fromByteArray(raw);
        CompoundTag viaBorrowGzipped = NbtFactory.borrowFromByteArray(gzipped).materialize();

        assertEquals(viaProductionRaw, viaBorrowGzipped,
            "borrowFromByteArray must transparently inflate gzipped input");
    }

    @Test
    @DisplayName("borrow tree survives original byte[] reference being dropped + GC")
    void borrowSurvivesInputReferenceDrop() throws IOException {
        // Buffer-retention invariant: the BorrowedCompoundTag retains a strong reference to the
        // (decompressed) bytes through Tape. Drop the local reference to the original input,
        // overwrite the input with junk to make the address unreachable through that path, hint
        // the GC, then materialize the borrow and assert it matches a fresh production decode.
        Path file = CORPUS_DIR.resolve("hypixel.nbt");
        byte[] originalPayload = Files.readAllBytes(file);

        CompoundTag expected = NbtFactory.fromByteArray(originalPayload);

        // Hold a fresh copy for the borrow API and the expected reference; the borrow must keep
        // its own copy alive once we null `payloadHolder[0]`.
        byte[][] payloadHolder = new byte[][]{Files.readAllBytes(file)};
        WeakReference<byte[]> payloadWeak = new WeakReference<>(payloadHolder[0]);

        BorrowedCompoundTag borrowed = NbtFactory.borrowFromByteArray(payloadHolder[0]);
        payloadHolder[0] = null;

        // Best-effort GC nudge - the Tape's strong reference to the buffer should keep it alive
        // regardless of how aggressively the GC runs. If the buffer were collected, materialize()
        // would either NPE or read uninitialized memory and produce a different tree.
        for (int i = 0; i < 5; i++) {
            System.gc();
            try {
                Thread.sleep(10);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Suppress unused-variable warning on the weak reference; its presence is documentation
        // that we are deliberately observing - not preventing - GC of the original allocation.
        // The Tape holds a strong reference, so the buffer must remain reachable.
        assertNotNull(payloadWeak, "weak reference sentinel allocated");

        CompoundTag viaBorrow = borrowed.materialize();
        assertEquals(expected, viaBorrow,
            "borrowed tree must remain valid after the original byte[] reference is dropped");
    }

    @Test
    @DisplayName("empty input throws a clear NbtException, not an opaque EOFException")
    void emptyInputRejected() {
        NbtException exception = assertThrows(NbtException.class,
            () -> NbtFactory.borrowFromByteArray(new byte[0]),
            "empty input must surface as NbtException");

        // The wrapped cause should not be a raw EOFException leaking from the streaming path - the
        // tape parser bounds-checks before reading and surfaces a descriptive NbtException.
        Throwable cause = exception.getCause();
        // Either no cause (parser threw NbtException directly) or the cause is itself an
        // NbtException - never a bare EOFException.
        if (cause != null)
            assertTrue(!(cause instanceof java.io.EOFException),
                "empty input must not surface as a raw EOFException, got: " + cause);
    }

    @Test
    @DisplayName("malformed input throws NbtException")
    void malformedInputRejected() {
        // 100 bytes of pseudo-random data; deterministic seed so the test is reproducible.
        byte[] junk = new byte[100];

        for (int i = 0; i < junk.length; i++)
            junk[i] = (byte) (i * 31 + 17);

        assertThrows(NbtException.class,
            () -> NbtFactory.borrowFromByteArray(junk),
            "100 bytes of junk must surface as NbtException");
    }

    @Test
    @DisplayName("non-compound root id surfaces as NbtException")
    void nonCompoundRootRejected() {
        // 0x42 is not TAG_Compound. Same case the TapeParser already covers, but exercised through
        // the public entry point to confirm the wrapping path preserves the diagnostic.
        byte[] payload = new byte[]{0x42, 0x00, 0x00};

        assertThrows(NbtException.class,
            () -> NbtFactory.borrowFromByteArray(payload),
            "non-compound root must surface as NbtException through borrowFromByteArray");
    }

}
