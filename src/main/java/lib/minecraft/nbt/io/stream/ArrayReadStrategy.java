package lib.minecraft.nbt.io.stream;

import java.io.BufferedInputStream;
import java.io.DataInputStream;

/**
 * Strategy for decoding {@code int[]} and {@code long[]} payloads inside {@link NbtInputStream}.
 *
 * <p>Two implementations exist so JMH can pick the winner for the workload at hand. Both are
 * required to produce bit-identical output - the parameterized correctness test in
 * {@code ArrayStrategyTest} pins this contract.</p>
 *
 * <ul>
 *   <li><b>STREAMWISE</b> - reads each element directly through {@link DataInputStream#readInt}
 *       / {@link DataInputStream#readLong}. No scratch allocation. Relies on the underlying
 *       {@link BufferedInputStream} to amortize the per-byte cost and on the JIT to
 *       intrinsify the big-endian conversion. Best when the JIT successfully inlines the call
 *       chain - typical for the auction workload's many small arrays.</li>
 *   <li><b>CHUNKED_THREADLOCAL</b> - bulk-reads into a 64 KiB thread-local scratch buffer and
 *       decodes through the {@link lib.minecraft.nbt.io.NbtByteCodec} {@code VarHandle} path.
 *       Arrays larger than the cap are decoded in 64 KiB chunks; the {@link ThreadLocal} never
 *       retains a buffer larger than that, so virtual threads and fork-join carriers pay a fixed
 *       per-thread footprint. Best when the JIT's inlining budget for the streamwise path is
 *       saturated by a hot megabyte-scale array.</li>
 * </ul>
 *
 * <p>The default strategy used by the no-arg {@link NbtInputStream#NbtInputStream(java.io.InputStream)}
 * constructor is selected based on the JMH report from Phase D of the optimization plan. Pass an
 * explicit value to {@link NbtInputStream#NbtInputStream(java.io.InputStream, ArrayReadStrategy)}
 * to override.</p>
 *
 * @see ArrayWriteStrategy
 * @see NbtInputStream
 */
public enum ArrayReadStrategy {

    /**
     * Element-by-element read through {@link DataInputStream#readInt} /
     * {@link DataInputStream#readLong}. No scratch allocation per call.
     */
    STREAMWISE,

    /**
     * Bulk read into a 64 KiB {@link ThreadLocal} scratch buffer, decode via
     * {@link lib.minecraft.nbt.io.NbtByteCodec}. Arrays larger than the cap are processed in
     * fixed-size chunks; the thread-local never grows past the cap.
     */
    CHUNKED_THREADLOCAL

}
