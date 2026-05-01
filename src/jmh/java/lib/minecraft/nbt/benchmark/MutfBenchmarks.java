package lib.minecraft.nbt.benchmark;

import lib.minecraft.nbt.io.NbtModifiedUtf8;
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

import java.io.UTFDataFormatException;
import java.util.concurrent.TimeUnit;

/**
 * Pure-string MUTF-8 decode microbenchmark. Establishes the baseline numbers that Phase B2's
 * 8-byte ASCII probe must beat by {@code >= 25%} on the {@code ascii-100} case.
 *
 * <p>The three {@link #payload} variants exercise the three paths the decoder handles:</p>
 * <ul>
 *   <li><b>ascii-100</b> - 100 bytes of pure ASCII, the dominant case for Hypixel item NBT
 *       (lore lines, enchantment ids, item ids).</li>
 *   <li><b>ascii-32</b> - 32 bytes of pure ASCII, exact 8-byte chunk boundary x4 - tail
 *       handling does not kick in.</li>
 *   <li><b>bmp-mixed-32</b> - 32 bytes mostly ASCII with a 2-byte UTF-8 BMP character in the
 *       middle, forcing the slow-path decoder.</li>
 * </ul>
 *
 * <p>Run with:</p>
 * <pre>
 *   ./gradlew jmh -PjmhInclude=Mutf
 * </pre>
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 5)
@Fork(1)
public class MutfBenchmarks {

    @Param({"ascii-100", "ascii-32", "bmp-mixed-32"})
    public String payload;

    private byte[] bytes;
    private int byteLength;

    @Setup(Level.Trial)
    public void setup() {
        this.bytes = switch (this.payload) {
            case "ascii-100" -> "the quick brown fox jumps over the lazy dog the quick brown fox jumps over the lazy dog!".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            case "ascii-32"  -> "abcdefghijklmnopqrstuvwxyz012345".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            case "bmp-mixed-32" -> mixedBmp32();
            default -> throw new IllegalArgumentException("unknown payload: " + this.payload);
        };

        if (this.bytes.length != 100 && this.bytes.length != 32)
            throw new IllegalStateException("payload " + this.payload + " has length " + this.bytes.length);
        this.byteLength = this.bytes.length;
        System.err.println("payload-size: " + this.payload + "=" + this.byteLength);
    }

    /**
     * Builds a 32-byte modified-UTF-8 sequence with a single 2-byte BMP character ({@code é},
     * {@code U+00E9}) in the middle. The encoded result is exactly 32 bytes - 30 ASCII bytes plus
     * the 2-byte sequence {@code 0xC3 0xA9}.
     */
    private static byte[] mixedBmp32() {
        // 15 ASCII + 'é' (2 bytes in UTF-8/MUTF-8) + 15 ASCII = 32 bytes total.
        byte[] out = new byte[32];
        byte[] left = "the quick brown".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] right = "fox jumps over!".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(left, 0, out, 0, 15);
        out[15] = (byte) 0xC3;
        out[16] = (byte) 0xA9;
        System.arraycopy(right, 0, out, 17, 15);
        return out;
    }

    @Benchmark
    public String decode(BytesProcessed counter) throws UTFDataFormatException {
        counter.payloadBytes += this.byteLength;
        return NbtModifiedUtf8.decode(this.bytes, 0, this.byteLength);
    }

    /**
     * Per-thread aux counter exposing the bytes processed by each invocation. Lets
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
