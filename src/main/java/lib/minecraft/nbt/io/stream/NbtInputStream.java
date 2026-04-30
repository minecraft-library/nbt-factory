package lib.minecraft.nbt.io.stream;

import lib.minecraft.nbt.io.NbtByteCodec;
import lib.minecraft.nbt.io.NbtInput;
import lib.minecraft.nbt.io.buffer.NbtInputBuffer;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * NBT deserialization that reads Minecraft's canonical big-endian binary wire format from an
 * arbitrary {@link InputStream} - suitable for files, network sockets, and GZIP-wrapped payloads
 * alike.
 *
 * <p>Decodes Java Edition's binary NBT layout exactly as documented on the
 * <a href="https://minecraft.wiki/w/NBT_format">Minecraft Wiki NBT format</a> page. Primitive
 * byte-level reads and the modified-UTF-8 string decoder are inherited unchanged from
 * {@link DataInputStream}, which natively speaks big-endian and consumes the 2-byte length
 * prefix + modified-UTF-8 framing that NBT uses for every string. {@code readListTag} and
 * {@code readCompoundTag} are inherited from the {@link NbtInput} defaults, which encode the
 * {@code (type, name, value)} + {@code TAG_End} compound framing and the
 * {@code element-type + big-endian length} list framing.</p>
 *
 * <p>The constructor wraps the provided stream in a {@link BufferedInputStream} unless it is
 * already buffered, so callers passing a raw {@code FileInputStream}, {@code GZIPInputStream},
 * or socket stream do not pay per-byte syscall overhead through {@code DataInputStream}. The
 * bulk primitive array reads ({@code readByteArray}, {@code readIntArray}, {@code readLongArray})
 * are overridden to dispatch on the configured {@link ArrayReadStrategy} - see that enum's
 * javadoc for the streamwise vs. chunked thread-local trade-off.</p>
 *
 * <p>Implements {@link NbtInput} on top of {@link DataInputStream} rather than wrapping it so
 * callers can use this directly in either role.</p>
 *
 * @see NbtInput
 * @see NbtInputBuffer
 * @see ArrayReadStrategy
 * @see <a href="https://minecraft.wiki/w/NBT_format">Minecraft Wiki - NBT format</a>
 */
@SuppressWarnings("all")
public class NbtInputStream extends DataInputStream implements NbtInput {

    /**
     * Default strategy used by the no-arg constructor. Phase F of the optimization plan flips
     * this to whichever strategy wins the JMH comparison.
     */
    static final ArrayReadStrategy DEFAULT_STRATEGY = ArrayReadStrategy.STREAMWISE;

    /**
     * Maximum size (bytes) of the {@link ThreadLocal} scratch buffer used by
     * {@link ArrayReadStrategy#CHUNKED_THREADLOCAL}. Arrays larger than the cap are decoded in
     * chunks of this size; the thread-local never retains anything larger so per-carrier footprint
     * stays bounded for virtual threads and fork-join pools.
     */
    private static final int SCRATCH_CAP_BYTES = 65_536;

    private static final ThreadLocal<byte[]> SCRATCH = ThreadLocal.withInitial(() -> new byte[SCRATCH_CAP_BYTES]);

    private final ArrayReadStrategy strategy;

    public NbtInputStream(@NotNull InputStream inputStream) throws IOException {
        this(inputStream, DEFAULT_STRATEGY);
    }

    public NbtInputStream(@NotNull InputStream inputStream, @NotNull ArrayReadStrategy strategy) throws IOException {
        super(inputStream instanceof BufferedInputStream ? inputStream : new BufferedInputStream(inputStream));
        this.strategy = strategy;
    }

    /**
     * Returns the array-decode strategy this stream was constructed with.
     */
    public @NotNull ArrayReadStrategy getStrategy() {
        return this.strategy;
    }

    @Override
    public byte @NotNull [] readByteArray() throws IOException {
        byte[] data = new byte[this.readInt()];
        this.readFully(data);
        return data;
    }

    @Override
    public int @NotNull [] readIntArray() throws IOException {
        int length = this.readInt();
        int[] data = new int[length];

        if (this.strategy == ArrayReadStrategy.STREAMWISE) {
            for (int i = 0; i < length; i++)
                data[i] = this.readInt();

            return data;
        }

        // CHUNKED_THREADLOCAL: bulk-read into a capped thread-local scratch buffer in chunks,
        // decoding through NbtByteCodec's VarHandle path.
        byte[] scratch = SCRATCH.get();
        int elementsPerChunk = SCRATCH_CAP_BYTES >>> 2; // 16384 ints per pass
        int remaining = length;
        int writeIndex = 0;

        while (remaining > 0) {
            int chunkElements = Math.min(remaining, elementsPerChunk);
            int chunkBytes = chunkElements << 2;
            this.readFully(scratch, 0, chunkBytes);

            int p = 0;
            for (int i = 0; i < chunkElements; i++) {
                data[writeIndex++] = NbtByteCodec.getInt(scratch, p);
                p += 4;
            }

            remaining -= chunkElements;
        }

        return data;
    }

    @Override
    public long @NotNull [] readLongArray() throws IOException {
        int length = this.readInt();
        long[] data = new long[length];

        if (this.strategy == ArrayReadStrategy.STREAMWISE) {
            for (int i = 0; i < length; i++)
                data[i] = this.readLong();

            return data;
        }

        // CHUNKED_THREADLOCAL
        byte[] scratch = SCRATCH.get();
        int elementsPerChunk = SCRATCH_CAP_BYTES >>> 3; // 8192 longs per pass
        int remaining = length;
        int writeIndex = 0;

        while (remaining > 0) {
            int chunkElements = Math.min(remaining, elementsPerChunk);
            int chunkBytes = chunkElements << 3;
            this.readFully(scratch, 0, chunkBytes);

            int p = 0;
            for (int i = 0; i < chunkElements; i++) {
                data[writeIndex++] = NbtByteCodec.getLong(scratch, p);
                p += 8;
            }

            remaining -= chunkElements;
        }

        return data;
    }

}
