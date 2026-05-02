package lib.minecraft.nbt.benchmark;

import lib.minecraft.nbt.io.NbtByteCodec;
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

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Phase D5 research probe - microbenchmark of {@link MemorySegment} reads against the existing
 * {@link NbtByteCodec} {@code byte[]} VarHandle reads.
 *
 * <p>The borrow API's hot read paths ({@code BorrowedLongTag.getLongValue},
 * {@code MutfStringView.equalsString}, {@code RawList} per-element decode) currently dispatch to
 * {@link NbtByteCodec#getInt(byte[], int)} / {@code getLong} / {@code getShort}. Risk #1 in the
 * Track A/B/C parity plan and the only high-prob + high-impact entry on the Track D research said
 * "do not use {@code MemorySegment} in the borrow parser". This probe measures whether that
 * warning still holds for read-only navigators on JDK 21.</p>
 *
 * <p>Each benchmark iterates every aligned {@code int}/{@code long}/{@code short} in a
 * 32/256/2048-byte buffer and folds the result into a sink so C2 cannot dead-code the loop. The
 * three sizes bracket the regime the borrow API operates in: a typical NBT key is 4-32 bytes,
 * a typical primitive value is 4-8 bytes, and an array tag's bulk decode runs over 100s of
 * bytes.</p>
 *
 * <p>Decision gate: if {@code segmentHeap*} beats {@code byteArray*} by &gt;= 5% on at least 2 of 3
 * buffer sizes, escalate to wiring 2-3 hot read sites in {@code Tape} to use a
 * {@link MemorySegment} view; otherwise, this probe is the deliverable - committed evidence that
 * {@code MemorySegment} does not help on small heap-backed reads with our access pattern.</p>
 *
 * <p>{@code java.lang.foreign} is a preview API in JDK 21 (final in JDK 22+). The build's JMH
 * source set is configured with {@code --enable-preview}; production code in {@code main}/
 * {@code test} deliberately does not use FFM, so consumers of {@code nbt-factory} are not forced
 * to enable preview features.</p>
 *
 * <p>Run with:</p>
 * <pre>
 *   ./gradlew jmh -PjmhInclude=MemorySegmentReadProbe
 * </pre>
 */
@BenchmarkMode({Mode.AverageTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 5)
@Fork(1)
public class MemorySegmentReadProbeBenchmarks {

    private static final ValueLayout.OfInt INT_BE =
        ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfLong LONG_BE =
        ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfShort SHORT_BE =
        ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);

    /**
     * Buffer sizes covering the regime the borrow API hot-read paths operate in:
     * <ul>
     *   <li>{@code 32} - typical NBT key length and a few primitive values; small enough that
     *       fixed-cost overhead dominates.</li>
     *   <li>{@code 256} - typical small string + a couple of primitive values bundled together.</li>
     *   <li>{@code 2048} - a primitive array tag (e.g., {@code IntArrayTag} or chunk biome
     *       data); bulk decode regime where amortized per-read cost matters.</li>
     * </ul>
     */
    @Param({"32", "256", "2048"})
    public int bufferSize;

    private byte[] buffer;
    private MemorySegment heapSegment;

    @Setup(Level.Trial)
    public void setup() {
        Random random = new Random(0xCAFEBABEL);
        this.buffer = new byte[this.bufferSize];
        random.nextBytes(this.buffer);
        this.heapSegment = MemorySegment.ofArray(this.buffer);
    }

    // ------------------------------------------------------------------
    // Int reads
    // ------------------------------------------------------------------

    @Benchmark
    public int byteArrayInts() {
        int sum = 0;
        byte[] buf = this.buffer;

        for (int off = 0; off + 4 <= buf.length; off += 4)
            sum += NbtByteCodec.getInt(buf, off);

        return sum;
    }

    @Benchmark
    public int segmentHeapInts() {
        int sum = 0;
        MemorySegment seg = this.heapSegment;
        long size = seg.byteSize();

        for (long off = 0; off + 4 <= size; off += 4)
            sum += seg.get(INT_BE, off);

        return sum;
    }

    // ------------------------------------------------------------------
    // Long reads
    // ------------------------------------------------------------------

    @Benchmark
    public long byteArrayLongs() {
        long sum = 0L;
        byte[] buf = this.buffer;

        for (int off = 0; off + 8 <= buf.length; off += 8)
            sum += NbtByteCodec.getLong(buf, off);

        return sum;
    }

    @Benchmark
    public long segmentHeapLongs() {
        long sum = 0L;
        MemorySegment seg = this.heapSegment;
        long size = seg.byteSize();

        for (long off = 0; off + 8 <= size; off += 8)
            sum += seg.get(LONG_BE, off);

        return sum;
    }

    // ------------------------------------------------------------------
    // Short reads (covers the 2-byte UTF-8 length-prefix path used for every
    // string and key decode on the borrow path).
    // ------------------------------------------------------------------

    @Benchmark
    public int byteArrayShorts() {
        int sum = 0;
        byte[] buf = this.buffer;

        for (int off = 0; off + 2 <= buf.length; off += 2)
            sum += NbtByteCodec.getUnsignedShort(buf, off);

        return sum;
    }

    @Benchmark
    public int segmentHeapShorts() {
        int sum = 0;
        MemorySegment seg = this.heapSegment;
        long size = seg.byteSize();

        for (long off = 0; off + 2 <= size; off += 2)
            sum += seg.get(SHORT_BE, off) & 0xFFFF;

        return sum;
    }

}
