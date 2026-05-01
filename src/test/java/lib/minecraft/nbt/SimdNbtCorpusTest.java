package lib.minecraft.nbt;

import lib.minecraft.nbt.tags.collection.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sanity check that every fixture vendored from {@code simdnbt}'s test corpus decodes via
 * {@link NbtFactory#fromByteArray(byte[])}.
 *
 * <p>Performance is intentionally not asserted - this test only proves the bytes survived
 * vendoring and the JMH benchmarks have a working corpus to point at. Throughput numbers come
 * from the {@code SimdNbtParityBenchmarks} class instead.</p>
 */
class SimdNbtCorpusTest {

    private static final Path CORPUS_DIR = Paths.get("src/test/resources/simdnbt-corpus");

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
        "complex_player.dat",
        "hypixel.nbt",
        "level.dat",
        "bigtest.nbt",
        "simple_player.dat",
        "hello_world.nbt",
        "inttest1023.nbt"
    })
    @DisplayName("simdnbt corpus fixture decodes via NbtFactory.fromByteArray")
    void corpusFileDecodes(String filename) throws IOException {
        Path file = CORPUS_DIR.resolve(filename);
        assertTrue(Files.exists(file), "corpus fixture missing: " + file);

        byte[] payload = readPossiblyGzipped(file);
        assertNotNull(payload);
        assertTrue(payload.length > 0, "decoded payload was empty: " + filename);

        CompoundTag root = NbtFactory.fromByteArray(payload);
        assertNotNull(root, "NbtFactory.fromByteArray returned null for " + filename);
        assertTrue(root.size() > 0, "decoded compound was empty for " + filename);
    }

    /**
     * Reads {@code file}, peeking at the first two bytes to decide whether to gzip-decode. The
     * simdnbt corpus mixes raw NBT (the {@code .nbt} files) with gzipped NBT (the {@code .dat}
     * files); both shapes need to round-trip through {@link NbtFactory#fromByteArray}.
     */
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
