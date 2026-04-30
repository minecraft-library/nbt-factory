package lib.minecraft.nbt.io.stream;

/**
 * Strategy for encoding {@code int[]} and {@code long[]} payloads inside {@link NbtOutputStream}.
 *
 * <p>Two implementations exist so JMH can pick the winner for the workload at hand. Both are
 * required to produce bit-identical output - the parameterized correctness test in
 * {@code ArrayStrategyTest} pins this contract, and any payload written with one strategy must
 * read back unchanged through either {@link ArrayReadStrategy} or the in-memory buffer path.</p>
 *
 * <ul>
 *   <li><b>STREAMWISE</b> - writes each element directly through {@link java.io.DataOutputStream#writeInt}
 *       / {@link java.io.DataOutputStream#writeLong}. No scratch allocation. Relies on the
 *       downstream buffering (typically a {@link java.io.BufferedOutputStream}) to amortize the
 *       per-byte cost and on the JIT to intrinsify the big-endian conversion.</li>
 *   <li><b>CHUNKED_THREADLOCAL</b> - encodes into a 64 KiB thread-local scratch buffer through
 *       the {@link lib.minecraft.nbt.io.NbtByteCodec} {@code VarHandle} path, then flushes each
 *       chunk with a single {@link java.io.OutputStream#write(byte[], int, int)} call. Arrays
 *       larger than the cap are written in 64 KiB chunks; the {@link ThreadLocal} never retains
 *       a buffer larger than that, so virtual threads and fork-join carriers pay a fixed
 *       per-thread footprint.</li>
 * </ul>
 *
 * <p>The default strategy used by the no-arg {@link NbtOutputStream#NbtOutputStream(java.io.OutputStream)}
 * constructor is selected based on the JMH report from Phase D of the optimization plan. Pass an
 * explicit value to {@link NbtOutputStream#NbtOutputStream(java.io.OutputStream, ArrayWriteStrategy)}
 * to override.</p>
 *
 * @see ArrayReadStrategy
 * @see NbtOutputStream
 */
public enum ArrayWriteStrategy {

    /**
     * Element-by-element write through {@link java.io.DataOutputStream#writeInt} /
     * {@link java.io.DataOutputStream#writeLong}. No scratch allocation per call.
     */
    STREAMWISE,

    /**
     * Encode into a 64 KiB {@link ThreadLocal} scratch buffer via
     * {@link lib.minecraft.nbt.io.NbtByteCodec}, flush each chunk with a single
     * {@link java.io.OutputStream#write(byte[], int, int)} call. Arrays larger than the cap are
     * processed in fixed-size chunks; the thread-local never grows past the cap.
     */
    CHUNKED_THREADLOCAL

}
