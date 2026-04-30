package lib.minecraft.nbt.benchmark;

import lib.minecraft.nbt.io.stream.ArrayReadStrategy;
import lib.minecraft.nbt.io.stream.ArrayWriteStrategy;
import lib.minecraft.nbt.io.stream.NbtInputStream;
import lib.minecraft.nbt.io.stream.NbtOutputStream;
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
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Phase D - JMH comparison of {@link ArrayReadStrategy} / {@link ArrayWriteStrategy} on
 * megabyte-scale {@code int[]} / {@code long[]} payloads. Phase F flips the default constructor
 * to whichever strategy wins.
 *
 * <p>Run with:</p>
 * <pre>
 *   ./gradlew jmh -PjmhInclude=NbtArrayStrategyBenchmarks
 * </pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class NbtArrayStrategyBenchmarks {

    /** Element count for the int payload (1 MiB / 4 bytes). */
    private static final int INT_ELEMENTS = 1 << 18;

    /** Element count for the long payload (1 MiB / 8 bytes). */
    private static final int LONG_ELEMENTS = 1 << 17;

    @Param({"STREAMWISE", "CHUNKED_THREADLOCAL"})
    public String strategyName;

    private ArrayReadStrategy readStrategy;
    private ArrayWriteStrategy writeStrategy;

    private int[] intData;
    private long[] longData;

    private byte[] intPayload;
    private byte[] longPayload;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        this.readStrategy = ArrayReadStrategy.valueOf(this.strategyName);
        this.writeStrategy = ArrayWriteStrategy.valueOf(this.strategyName);

        Random r = new Random(0xCAFEBABE);
        this.intData = new int[INT_ELEMENTS];
        for (int i = 0; i < INT_ELEMENTS; i++) this.intData[i] = r.nextInt();

        this.longData = new long[LONG_ELEMENTS];
        for (int i = 0; i < LONG_ELEMENTS; i++) this.longData[i] = r.nextLong();

        // Pre-encode read payloads using the streamwise path (bit-identical to chunked).
        ByteArrayOutputStream intBaos = new ByteArrayOutputStream(4 + (INT_ELEMENTS << 2));
        try (NbtOutputStream out = new NbtOutputStream(intBaos, ArrayWriteStrategy.STREAMWISE)) {
            out.writeIntArray(this.intData);
        }
        this.intPayload = intBaos.toByteArray();

        ByteArrayOutputStream longBaos = new ByteArrayOutputStream(4 + (LONG_ELEMENTS << 3));
        try (NbtOutputStream out = new NbtOutputStream(longBaos, ArrayWriteStrategy.STREAMWISE)) {
            out.writeLongArray(this.longData);
        }
        this.longPayload = longBaos.toByteArray();
    }

    // ---------------------------------------------------------------------
    // Read benchmarks
    // ---------------------------------------------------------------------

    @Benchmark
    public int[] largeIntArray_read() throws Exception {
        try (NbtInputStream in = new NbtInputStream(new ByteArrayInputStream(this.intPayload), this.readStrategy)) {
            return in.readIntArray();
        }
    }

    @Benchmark
    public long[] largeLongArray_read() throws Exception {
        try (NbtInputStream in = new NbtInputStream(new ByteArrayInputStream(this.longPayload), this.readStrategy)) {
            return in.readLongArray();
        }
    }

    // ---------------------------------------------------------------------
    // Write benchmarks
    // ---------------------------------------------------------------------

    @Benchmark
    public byte[] largeIntArray_write() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(4 + (INT_ELEMENTS << 2));
        try (NbtOutputStream out = new NbtOutputStream(baos, this.writeStrategy)) {
            out.writeIntArray(this.intData);
        }
        return baos.toByteArray();
    }

    @Benchmark
    public byte[] largeLongArray_write() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(4 + (LONG_ELEMENTS << 3));
        try (NbtOutputStream out = new NbtOutputStream(baos, this.writeStrategy)) {
            out.writeLongArray(this.longData);
        }
        return baos.toByteArray();
    }

}
