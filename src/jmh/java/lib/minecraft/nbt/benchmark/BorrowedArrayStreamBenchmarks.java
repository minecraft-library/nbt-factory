package lib.minecraft.nbt.benchmark;

import lib.minecraft.nbt.NbtFactory;
import lib.minecraft.nbt.borrow.BorrowedCompoundTag;
import lib.minecraft.nbt.borrow.BorrowedIntArrayTag;
import lib.minecraft.nbt.borrow.BorrowedLongArrayTag;
import lib.minecraft.nbt.tags.array.IntArrayTag;
import lib.minecraft.nbt.tags.array.LongArrayTag;
import lib.minecraft.nbt.tags.collection.CompoundTag;
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

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Phase D4 paired-benchmark: {@code longStream()} on borrowed array tags vs the existing
 * {@code toIntArray()} / {@code toLongArray()} accessors.
 *
 * <p>The hypothesis was that for a sum-style reduction, a per-element spliterator stream avoids
 * both the primitive-array allocation and the second pass over the materialized array. The
 * hypothesis held for {@code longStream()} (~10x win over {@code toLongArray} + bulk sum) but
 * failed for {@code intStream()}, which D4 measured 27% slower than
 * {@code Arrays.stream(toIntArray()).sum()} because the latter benefits from C2 auto-vectorized
 * bulk byteswap that the per-element spliterator could not match. Phase E1 retired
 * {@code intStream()} and its benchmark accordingly. {@link #sumViaToIntArray()} is retained as
 * the now-obvious-faster int-side control.</p>
 *
 * <p>Setup builds a synthetic compound with one large primitive array, encodes it once outside the
 * timed region, and parks the borrowed array tag on a benchmark-scoped field. Each benchmark
 * method's hot loop reuses the same retained borrow + buffer; only the iteration / reduction
 * strategy changes.</p>
 *
 * <p>Run with:</p>
 * <pre>
 *   ./gradlew jmh -PjmhInclude=BorrowedArrayStream
 * </pre>
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 5)
@Fork(1)
public class BorrowedArrayStreamBenchmarks {

    @Param({"1024", "65536"})
    public int count;

    private BorrowedIntArrayTag borrowedInts;

    private BorrowedLongArrayTag borrowedLongs;

    @Setup(Level.Trial)
    public void setup() {
        // Build a hypixel-shape compound with a single big primitive array and a couple of leaf
        // entries to keep the surrounding tape realistic. Reduction is over the array, so the
        // surrounding entries are noise to the timed region but make sure the borrow path isn't
        // running on a degenerate top-level shape.
        // Knuth's golden-ratio mixers, downcast so the literals fit a signed 32 / 64 bit slot.
        final int intMix = (int) 2_654_435_761L;
        final long longMix = 0x9E3779B97F4A7C15L;

        int[] ints = new int[this.count];
        for (int i = 0; i < this.count; i++)
            ints[i] = (i * intMix) ^ (i << 13); // diffusion; not random, but spread

        long[] longs = new long[this.count];
        for (int i = 0; i < this.count; i++)
            longs[i] = ((long) i * longMix) ^ ((long) i << 31);

        CompoundTag root = new CompoundTag();
        root.put("ints", new IntArrayTag(ints));
        root.put("longs", new LongArrayTag(longs));

        byte[] encoded = NbtFactory.toByteArray(root);
        BorrowedCompoundTag borrowedRoot = NbtFactory.borrowFromByteArray(encoded);
        this.borrowedInts = (BorrowedIntArrayTag) borrowedRoot.get("ints");
        this.borrowedLongs = (BorrowedLongArrayTag) borrowedRoot.get("longs");
    }

    /**
     * Materialize the {@code int[]} once, then sum it via {@link Arrays#stream(int[])}. After
     * Phase E1 this is the obvious choice for int-array reductions: the bulk-byteswap path
     * ({@link lib.minecraft.nbt.io.NbtByteCodec#getIntArrayBE NbtByteCodec.getIntArrayBE}) C2
     * auto-vectorizes cleanly, beating any per-element spliterator we tried.
     */
    @Benchmark
    public long sumViaToIntArray() {
        return Arrays.stream(this.borrowedInts.toIntArray()).asLongStream().sum();
    }

    /**
     * Control - materialize the {@code long[]} once, then sum it via {@link Arrays#stream(long[])}.
     */
    @Benchmark
    public long sumViaToLongArray() {
        return Arrays.stream(this.borrowedLongs.toLongArray()).sum();
    }

    /**
     * D4 fast path - {@code LongStream.sum()} on the spliterator-backed stream.
     */
    @Benchmark
    public long sumViaLongStream() {
        return this.borrowedLongs.longStream().sum();
    }

}
