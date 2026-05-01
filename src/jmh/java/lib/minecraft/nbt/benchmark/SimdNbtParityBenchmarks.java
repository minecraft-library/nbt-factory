package lib.minecraft.nbt.benchmark;

import lib.minecraft.nbt.NbtFactory;
import lib.minecraft.nbt.tags.collection.CompoundTag;
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

/**
 * JMH parity benchmarks decoding the same 5 fixtures simdnbt's own benchmarks decode.
 *
 * <p>Mirrors {@code simdnbt/benches/nbt_borrow.rs} and the headline
 * {@code simdnbt/benches/compare.rs} bench. Gzip is decoded once during {@code @Setup}; the
 * timed region is binary NBT only. The {@link BytesProcessed} {@code @AuxCounters} state
 * exposes a per-iteration byte count so {@code tools/jmh-report.py} can derive throughput in
 * MiB/s and GiB/s.</p>
 *
 * <p>The full corpus path lives at {@code src/test/resources/simdnbt-corpus/} and is vendored
 * verbatim from {@code https://git.matdoes.dev/mat/simdnbt} (see
 * {@code tools/fetch-simdnbt-corpus.sh}).</p>
 *
 * <p>Run with:</p>
 * <pre>
 *   ./gradlew jmh -PjmhInclude=SimdNbtParity
 * </pre>
 *
 * <p>Parity comparison vs simdnbt's mimalloc is approximate; Java has no direct mimalloc
 * analogue, so the G1 numbers reported here are the authoritative measurement.</p>
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 5)
@Fork(1)
public class SimdNbtParityBenchmarks {

    private static final Path CORPUS_DIR = Paths.get("src/test/resources/simdnbt-corpus");

    @Param({"complex_player.dat", "hypixel.nbt", "level.dat",
            "bigtest.nbt", "simple_player.dat"})
    public String filename;

    /** Gzip-decoded NBT payload, decoded once per trial. */
    private byte[] payload;

    /** Cached size for the {@link BytesProcessed} aux counter. */
    private int payloadBytes;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        Path file = CORPUS_DIR.resolve(this.filename);
        byte[] raw = Files.readAllBytes(file);

        // Gzip magic = 0x1F 0x8B. Decode once outside the timed region so the
        // benchmark measures NBT decode, not zlib inflation.
        if (raw.length >= 2 && (raw[0] & 0xff) == 0x1f && (raw[1] & 0xff) == 0x8b) {
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(raw));
                 ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length * 4)) {
                in.transferTo(out);
                this.payload = out.toByteArray();
            }
        } else {
            this.payload = raw;
        }

        this.payloadBytes = this.payload.length;
        // Print to stderr so jmh-report.py's --sizes-log fallback can pick it up.
        System.err.println("payload-size: " + this.filename + "=" + this.payloadBytes);
    }

    /**
     * Decode-only benchmark mirroring simdnbt's {@code simdnbt_owned_parse} and
     * {@code simdnbt_borrow_parse} cases. Increments the aux byte counter so JMH JSON
     * output carries the per-op byte count needed for MiB/s reporting.
     */
    @Benchmark
    public CompoundTag parse(BytesProcessed bytes) throws IOException {
        bytes.payloadBytes += this.payloadBytes;
        return NbtFactory.fromByteArray(this.payload);
    }

    /**
     * Per-thread aux counter exposing the bytes processed by each invocation. JMH adds
     * {@code "secondaryMetrics": {"payloadBytes": {...}}} to the JSON output so the report
     * script can compute MiB/s without relying on the {@link #filename} fallback table.
     */
    @AuxCounters(AuxCounters.Type.OPERATIONS)
    @State(Scope.Thread)
    public static class BytesProcessed {

        public long payloadBytes;

        @Setup(Level.Iteration)
        public void clear() {
            this.payloadBytes = 0;
        }

    }

}
