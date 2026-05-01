package lib.minecraft.nbt.benchmark;

import lib.minecraft.nbt.NbtFactory;
import lib.minecraft.nbt.borrow.BorrowedCompoundTag;
import lib.minecraft.nbt.borrow.BorrowedTag;
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
import org.openjdk.jmh.infra.Blackhole;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

/**
 * Borrow-mode JMH benchmarks comparing {@link NbtFactory#borrowFromByteArray(byte[])} against the
 * materializing {@link NbtFactory#fromByteArray(byte[])} path on the simdnbt parity corpus.
 *
 * <p>Phase C6 of the simdnbt-parity work: the moment-of-truth measurement for criterion C-G4
 * ({@code borrowDecode} >= 2x faster than {@code materializingDecode} on
 * {@code complex_player.dat}). Three benchmark methods per fixture:</p>
 *
 * <ul>
 *   <li><b>{@link #materializingDecode(BytesProcessed)}</b> - the existing eager-decode path. Same
 *       call as {@link SimdNbtParityBenchmarks#parse(SimdNbtParityBenchmarks.BytesProcessed) parse},
 *       repeated here for an apples-to-apples paired-bench layout.</li>
 *   <li><b>{@link #borrowDecode(BytesProcessed)}</b> - tape-encode only; lazy decode of every value
 *       deferred until access. This is the headline number for C-G4.</li>
 *   <li><b>{@link #borrowDecodeAndAccessRoot(Blackhole, BytesProcessed)}</b> - tape-encode plus a
 *       few cheap top-level accessor calls so escape analysis cannot optimize the borrow into a
 *       no-op on the basis that the tree is never observed.</li>
 * </ul>
 *
 * <p>Same gzip-decoded-once-in-{@code @Setup} pattern as {@link SimdNbtParityBenchmarks} - the
 * timed region is binary NBT only.</p>
 *
 * <p>Run with:</p>
 * <pre>
 *   ./gradlew jmh -PjmhInclude=Borrow
 * </pre>
 *
 * <p><b>Disclosure:</b> simdnbt::borrow holds slices into the input bytes (zero-copy strings +
 * arrays). Our {@link BorrowedCompoundTag} materializes strings via {@code MutfStringView.toString()}
 * on access and arrays via {@code RawList.toIntArray()} on access; the lazy-decode win is real
 * (unread fields are never decoded), but per-tag accessor allocation still dominates short-payload
 * throughput.</p>
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 5)
@Fork(1)
public class BorrowBenchmarks {

    private static final Path CORPUS_DIR = Paths.get("src/test/resources/simdnbt-corpus");

    @Param({"complex_player.dat", "hypixel.nbt", "level.dat",
            "bigtest.nbt", "simple_player.dat", "inttest1023.nbt"})
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
     * Materializing decode - the existing {@link NbtFactory#fromByteArray(byte[])} path. Allocates
     * a full owned tree. Paired against {@link #borrowDecode(BytesProcessed)} for the per-fixture
     * speedup ratio.
     */
    @Benchmark
    public CompoundTag materializingDecode(BytesProcessed bytes) {
        bytes.payloadBytes += this.payloadBytes;
        return NbtFactory.fromByteArray(this.payload);
    }

    /**
     * Borrow decode - {@link NbtFactory#borrowFromByteArray(byte[])} returns a tape-encoded view
     * with no eager value decode. Returning the {@link BorrowedCompoundTag} keeps JMH from dead-
     * coding the call, but no fields are observed - the JIT is free to drop work for any value
     * that escape analysis can prove unreachable downstream.
     */
    @Benchmark
    public BorrowedCompoundTag borrowDecode(BytesProcessed bytes) {
        bytes.payloadBytes += this.payloadBytes;
        return NbtFactory.borrowFromByteArray(this.payload);
    }

    /**
     * Borrow decode plus a handful of top-level accessor calls. Forces the tape header to be
     * observed and a small number of named-key lookups to actually run, so escape analysis cannot
     * prove the borrow tree unobserved. Touching only the root level keeps the lazy-decode win
     * intact - the bulk of the tree still goes unread.
     */
    @Benchmark
    public void borrowDecodeAndAccessRoot(Blackhole blackhole, BytesProcessed bytes) {
        bytes.payloadBytes += this.payloadBytes;
        BorrowedCompoundTag root = NbtFactory.borrowFromByteArray(this.payload);
        // Top-level header read + a handful of common-name lookups. None of these
        // strings is guaranteed to exist on every fixture; null returns are fine and
        // are themselves consumed by the Blackhole.
        blackhole.consume(root.size());
        blackhole.consume(root.containsKey(""));
        BorrowedTag<?> a = root.get("Data");        // level.dat root
        BorrowedTag<?> b = root.get("");            // bigtest.nbt anonymous root
        BorrowedTag<?> c = root.get("i");           // hypixel.nbt auctions list
        BorrowedTag<?> d = root.get("RootVehicle"); // complex_player.dat
        blackhole.consume(a);
        blackhole.consume(b);
        blackhole.consume(c);
        blackhole.consume(d);
    }

    /**
     * Per-thread aux counter exposing the bytes processed by each invocation. Mirrors
     * {@link SimdNbtParityBenchmarks.BytesProcessed} - JMH adds
     * {@code "secondaryMetrics": {"payloadBytes": {...}}} to the JSON output so the report script
     * can compute MiB/s without relying on the {@link #filename} fallback table.
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
