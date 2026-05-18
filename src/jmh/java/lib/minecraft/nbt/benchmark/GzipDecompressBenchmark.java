package lib.minecraft.nbt.benchmark;

import dev.simplified.util.compression.Compression;
import lib.minecraft.nbt.NbtFactory;
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

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Gzip-inflate regression scaffold. Compares the kept implementation against the prior
 * {@code Compression.decompress} baseline so the gzip path's allocation profile can be
 * re-verified after any future {@code NbtFactory} change.
 *
 * <ul>
 *   <li><b>baseline</b> - {@code Compression.decompress(bytes)} from the external
 *       {@code simplified-dev/utils} library; allocates a transient read buffer plus a growable
 *       accumulator per call.</li>
 *   <li><b>pooled</b> - {@code NbtFactory.decompressGzipPooled} reads the gzip ISIZE trailer to
 *       pre-size the output buffer at exact length, single allocation per call. This is the
 *       implementation called from {@code NbtFactory.fromByteArray} on gzip-prefixed input.</li>
 * </ul>
 *
 * <p>Input corpus is the Hypixel auction fixture
 * {@code src/test/resources/nbt-bench-fixture/auctions.bin} (~42 MB containing many independent
 * gzipped NBT payloads). Each {@code @Benchmark} iteration inflates every payload in the configured
 * slice; the {@code @AuxCounters payloadBytes} aggregator lets {@code tools/jmh-report.py} compute
 * throughput in MiB/s of compressed input.</p>
 *
 * <p>Run with:</p>
 * <pre>
 *   ./gradlew jmh -PjmhInclude=GzipDecompress
 * </pre>
 *
 * <p>For GC-pressure measurement (the real win mechanism), add the JMH gc profiler:</p>
 * <pre>
 *   ./gradlew jmh -PjmhInclude=GzipDecompress -PjmhProfilers=gc
 * </pre>
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 5)
@Fork(1)
public class GzipDecompressBenchmark {

    private static final Path AUCTION_FIXTURE = Paths.get("minecraft-api/src/test/resources/nbt-bench-fixture/auctions.bin");
    private static final Path AUCTION_FIXTURE_FALLBACK = Paths.get("src/test/resources/nbt-bench-fixture/auctions.bin");

    @Param({"single-auction", "batch-100", "batch-all"})
    public String corpus;

    private byte[][] payloads;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        Path fixture = Files.exists(AUCTION_FIXTURE) ? AUCTION_FIXTURE
            : Files.exists(AUCTION_FIXTURE_FALLBACK) ? AUCTION_FIXTURE_FALLBACK : null;

        if (fixture == null)
            throw new IOException("Auction fixture not found at " + AUCTION_FIXTURE + " or " + AUCTION_FIXTURE_FALLBACK
                + " - run ./gradlew generateAuctionFixture to populate it.");

        byte[][] all;
        try (DataInputStream in = new DataInputStream(Files.newInputStream(fixture))) {
            int count = in.readInt();
            all = new byte[count][];

            for (int i = 0; i < count; i++) {
                int len = in.readInt();
                byte[] payload = new byte[len];
                in.readFully(payload);
                all[i] = payload;
            }
        }

        int slice = switch (this.corpus) {
            case "single-auction" -> 1;
            case "batch-100" -> Math.min(100, all.length);
            case "batch-all" -> all.length;
            default -> throw new IllegalArgumentException("unknown corpus: " + this.corpus);
        };

        this.payloads = new byte[slice][];
        System.arraycopy(all, 0, this.payloads, 0, slice);
        System.err.println("corpus-size: " + this.corpus + "=" + slice + " payloads");

        // Parity check: every pooled output must equal the baseline inflate. A divergent
        // implementation would silently produce wrong timing numbers, so we fail the trial early.
        for (int i = 0; i < this.payloads.length; i++) {
            byte[] ref = Compression.decompress(this.payloads[i]);
            byte[] pooled = NbtFactory.decompressGzipPooled(this.payloads[i]);

            if (!java.util.Arrays.equals(ref, pooled))
                throw new IllegalStateException("pooled diverges from baseline at payload " + i
                    + " (ref=" + ref.length + "B, pooled=" + pooled.length + "B)");
        }
    }

    @Benchmark
    public void baseline(BytesProcessed counter, Blackhole bh) {
        for (byte[] p : this.payloads) {
            counter.payloadBytes += p.length;
            bh.consume(Compression.decompress(p));
        }
    }

    @Benchmark
    public void pooled(BytesProcessed counter, Blackhole bh) throws IOException {
        for (byte[] p : this.payloads) {
            counter.payloadBytes += p.length;
            bh.consume(NbtFactory.decompressGzipPooled(p));
        }
    }

    /**
     * Per-thread aux counter exposing the compressed byte volume processed by each invocation. Lets
     * {@code tools/jmh-report.py} compute MiB/s without consulting the fallback size table.
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
