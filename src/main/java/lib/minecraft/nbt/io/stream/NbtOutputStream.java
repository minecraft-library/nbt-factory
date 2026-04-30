package lib.minecraft.nbt.io.stream;

import lib.minecraft.nbt.io.NbtByteCodec;
import lib.minecraft.nbt.io.NbtOutput;
import lib.minecraft.nbt.io.buffer.NbtOutputBuffer;
import org.jetbrains.annotations.NotNull;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * NBT serialization that writes Minecraft's canonical big-endian binary wire format directly to
 * an arbitrary {@link OutputStream} - suitable for files, network sockets, and
 * compression-wrapping output streams alike.
 *
 * <p>Emits Java Edition's binary NBT layout exactly as documented on the
 * <a href="https://minecraft.wiki/w/NBT_format">Minecraft Wiki NBT format</a> page. Primitive
 * byte-level writes and the modified-UTF-8 string encoder are inherited unchanged from
 * {@link DataOutputStream}, which natively produces the big-endian bytes and the 2-byte length
 * prefix + modified-UTF-8 framing NBT uses for every string. {@code writeListTag} and
 * {@code writeCompoundTag} are inherited from the {@link NbtOutput} defaults so the
 * {@code (type, name, value)} + {@code TAG_End} compound framing and the
 * {@code element-type + big-endian length} list framing come in for free.</p>
 *
 * <p>The bulk primitive array writes ({@code writeByteArray}, {@code writeIntArray},
 * {@code writeLongArray}) are overridden to dispatch on the configured
 * {@link ArrayWriteStrategy} - see that enum's javadoc for the streamwise vs. chunked
 * thread-local trade-off.</p>
 *
 * <p>Implements {@link NbtOutput} on top of {@link DataOutputStream} rather than wrapping it so
 * callers can use this directly in either role.</p>
 *
 * @see NbtOutput
 * @see NbtOutputBuffer
 * @see ArrayWriteStrategy
 * @see <a href="https://minecraft.wiki/w/NBT_format">Minecraft Wiki - NBT format</a>
 */
public class NbtOutputStream extends DataOutputStream implements NbtOutput {

    /**
     * Default strategy used by the no-arg constructor.
     *
     * <p>Set to {@link ArrayWriteStrategy#CHUNKED_THREADLOCAL} based on Phase D JMH evidence:
     * encoding megabyte-scale {@code int[]} payloads is ~9x faster and {@code long[]} payloads
     * ~7x faster than the streamwise path, with {@link ArrayWriteStrategy#STREAMWISE} retained
     * as an explicit opt-in via the two-arg constructor for callers with unusual workloads.</p>
     */
    static final ArrayWriteStrategy DEFAULT_STRATEGY = ArrayWriteStrategy.CHUNKED_THREADLOCAL;

    /**
     * Maximum size (bytes) of the {@link ThreadLocal} scratch buffer used by
     * {@link ArrayWriteStrategy#CHUNKED_THREADLOCAL}. Arrays larger than the cap are encoded in
     * chunks of this size; the thread-local never retains anything larger so per-carrier
     * footprint stays bounded for virtual threads and fork-join pools.
     */
    private static final int SCRATCH_CAP_BYTES = 65_536;

    private static final ThreadLocal<byte[]> SCRATCH = ThreadLocal.withInitial(() -> new byte[SCRATCH_CAP_BYTES]);

    private final ArrayWriteStrategy strategy;

    public NbtOutputStream(@NotNull OutputStream outputStream) {
        this(outputStream, DEFAULT_STRATEGY);
    }

    public NbtOutputStream(@NotNull OutputStream outputStream, @NotNull ArrayWriteStrategy strategy) {
        super(outputStream);
        this.strategy = strategy;
    }

    /**
     * Returns the array-encode strategy this stream was constructed with.
     */
    public @NotNull ArrayWriteStrategy getStrategy() {
        return this.strategy;
    }

    @Override
    public void writeByteArray(byte @NotNull [] data) throws IOException {
        this.writeInt(data.length);
        this.write(data);
    }

    @Override
    public void writeIntArray(int @NotNull [] data) throws IOException {
        int length = data.length;
        this.writeInt(length);

        if (this.strategy == ArrayWriteStrategy.STREAMWISE) {
            for (int i = 0; i < length; i++)
                this.writeInt(data[i]);

            return;
        }

        // CHUNKED_THREADLOCAL: encode into the capped thread-local buffer in chunks, flushing
        // each chunk through a single write() call.
        byte[] scratch = SCRATCH.get();
        int elementsPerChunk = SCRATCH_CAP_BYTES >>> 2; // 16384 ints per pass
        int remaining = length;
        int readIndex = 0;

        while (remaining > 0) {
            int chunkElements = Math.min(remaining, elementsPerChunk);
            int p = 0;

            for (int i = 0; i < chunkElements; i++) {
                NbtByteCodec.putInt(scratch, p, data[readIndex++]);
                p += 4;
            }

            this.write(scratch, 0, chunkElements << 2);
            remaining -= chunkElements;
        }
    }

    @Override
    public void writeLongArray(long @NotNull [] data) throws IOException {
        int length = data.length;
        this.writeInt(length);

        if (this.strategy == ArrayWriteStrategy.STREAMWISE) {
            for (int i = 0; i < length; i++)
                this.writeLong(data[i]);

            return;
        }

        // CHUNKED_THREADLOCAL
        byte[] scratch = SCRATCH.get();
        int elementsPerChunk = SCRATCH_CAP_BYTES >>> 3; // 8192 longs per pass
        int remaining = length;
        int readIndex = 0;

        while (remaining > 0) {
            int chunkElements = Math.min(remaining, elementsPerChunk);
            int p = 0;

            for (int i = 0; i < chunkElements; i++) {
                NbtByteCodec.putLong(scratch, p, data[readIndex++]);
                p += 8;
            }

            this.write(scratch, 0, chunkElements << 3);
            remaining -= chunkElements;
        }
    }

}
