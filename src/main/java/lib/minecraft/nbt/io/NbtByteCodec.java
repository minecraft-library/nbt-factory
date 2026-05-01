package lib.minecraft.nbt.io;

import lombok.experimental.UtilityClass;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/**
 * Static codec for big-endian primitive reads and writes against a raw {@code byte[]}.
 *
 * <p>Java Edition NBT is always big-endian on the wire, so every primitive access in the
 * byte-array backends ({@link lib.minecraft.nbt.io.buffer.NbtInputBuffer NbtInputBuffer}
 * and {@link lib.minecraft.nbt.io.buffer.NbtOutputBuffer NbtOutputBuffer}) flows through
 * this class. The bulk primitive-array reads on the streaming backends
 * ({@link lib.minecraft.nbt.io.stream.NbtInputStream NbtInputStream},
 * {@link lib.minecraft.nbt.io.stream.NbtOutputStream NbtOutputStream}) also use it for
 * the scratch-buffer decode / encode step. See the
 * <a href="https://minecraft.wiki/w/NBT_format">Minecraft Wiki NBT format</a> for the on-wire
 * layout each method implements.</p>
 *
 * <p>Backed by {@link MethodHandles#byteArrayViewVarHandle} which the JIT intrinsifies to a single
 * machine instruction (typically {@code MOVBE} on x86-64, equivalent on ARM64). There is no
 * alignment requirement and no endianness branch - the VarHandle carries the big-endian contract
 * inside its implementation. Works identically on Linux and Windows, JDK 9+.</p>
 *
 * <p>All methods are static utilities - no per-call allocation, no instance state. The JIT
 * inlines them across the NBT I/O hot path so the generated code is equivalent to writing the
 * bit shifting directly at each call site, but the source-level duplication is gone.</p>
 */
@UtilityClass
public final class NbtByteCodec {

    private static final VarHandle SHORT_BE = MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.BIG_ENDIAN);
    private static final VarHandle INT_BE = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);
    private static final VarHandle LONG_BE = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);

    /**
     * Reads a big-endian signed 16-bit integer starting at {@code offset}.
     */
    public static short getShort(byte[] buffer, int offset) {
        return (short) SHORT_BE.get(buffer, offset);
    }

    /**
     * Reads a big-endian unsigned 16-bit integer starting at {@code offset}.
     */
    public static int getUnsignedShort(byte[] buffer, int offset) {
        return ((short) SHORT_BE.get(buffer, offset)) & 0xFFFF;
    }

    /**
     * Reads a big-endian signed 32-bit integer starting at {@code offset}.
     */
    public static int getInt(byte[] buffer, int offset) {
        return (int) INT_BE.get(buffer, offset);
    }

    /**
     * Reads a big-endian signed 64-bit integer starting at {@code offset}.
     */
    public static long getLong(byte[] buffer, int offset) {
        return (long) LONG_BE.get(buffer, offset);
    }

    /**
     * Reads a big-endian IEEE-754 single-precision float starting at {@code offset}.
     */
    public static float getFloat(byte[] buffer, int offset) {
        return Float.intBitsToFloat(getInt(buffer, offset));
    }

    /**
     * Reads a big-endian IEEE-754 double-precision float starting at {@code offset}.
     */
    public static double getDouble(byte[] buffer, int offset) {
        return Double.longBitsToDouble(getLong(buffer, offset));
    }

    /**
     * Writes a big-endian 16-bit value. The high bits of {@code value} above bit 15 are discarded.
     */
    public static void putShort(byte[] buffer, int offset, int value) {
        SHORT_BE.set(buffer, offset, (short) value);
    }

    /**
     * Writes a big-endian 32-bit value.
     */
    public static void putInt(byte[] buffer, int offset, int value) {
        INT_BE.set(buffer, offset, value);
    }

    /**
     * Writes a big-endian 64-bit value.
     */
    public static void putLong(byte[] buffer, int offset, long value) {
        LONG_BE.set(buffer, offset, value);
    }

    /**
     * Writes a big-endian IEEE-754 single-precision float.
     */
    public static void putFloat(byte[] buffer, int offset, float value) {
        putInt(buffer, offset, Float.floatToIntBits(value));
    }

    /**
     * Writes a big-endian IEEE-754 double-precision float.
     */
    public static void putDouble(byte[] buffer, int offset, double value) {
        putLong(buffer, offset, Double.doubleToLongBits(value));
    }

    // ------------------------------------------------------------------
    // Bulk array byteswap (Phase B1)
    // ------------------------------------------------------------------

    /**
     * Decodes {@code count} big-endian 32-bit integers from {@code src} starting at
     * {@code srcOffset} into {@code dst} starting at {@code dstOffset}.
     *
     * <p>Shaped as a single-strided {@link VarHandle} loop so C2 auto-vectorizes the byteswap on
     * every platform that can - x86-64 emits {@code MOVBE} per element and folds adjacent loads
     * into {@code vpshufb}-based SIMD when the loop trip count is large enough; ARM64 emits
     * {@code REV32}. The caller is responsible for bounds checking - this method assumes
     * {@code srcOffset + (count << 2) <= src.length} and {@code dstOffset + count <= dst.length}.</p>
     *
     * @param src raw big-endian bytes
     * @param srcOffset byte offset into {@code src} of the first element
     * @param dst destination {@code int[]}
     * @param dstOffset element offset into {@code dst} of the first element
     * @param count number of 32-bit integers to decode
     */
    public static void getIntArrayBE(byte[] src, int srcOffset, int[] dst, int dstOffset, int count) {
        for (int i = 0; i < count; i++)
            dst[dstOffset + i] = (int) INT_BE.get(src, srcOffset + (i << 2));
    }

    /**
     * Decodes {@code count} big-endian 64-bit integers from {@code src} starting at
     * {@code srcOffset} into {@code dst} starting at {@code dstOffset}.
     *
     * <p>Shaped identically to {@link #getIntArrayBE(byte[], int, int[], int, int)} so C2 can
     * apply the same auto-vectorization. Caller is responsible for bounds checking.</p>
     *
     * @param src raw big-endian bytes
     * @param srcOffset byte offset into {@code src} of the first element
     * @param dst destination {@code long[]}
     * @param dstOffset element offset into {@code dst} of the first element
     * @param count number of 64-bit integers to decode
     */
    public static void getLongArrayBE(byte[] src, int srcOffset, long[] dst, int dstOffset, int count) {
        for (int i = 0; i < count; i++)
            dst[dstOffset + i] = (long) LONG_BE.get(src, srcOffset + (i << 3));
    }

    /**
     * Encodes {@code count} 32-bit integers from {@code src} starting at {@code srcOffset} into
     * {@code dst} as big-endian bytes starting at {@code dstOffset}.
     *
     * <p>Shape matches {@link #getIntArrayBE(byte[], int, int[], int, int)} - tight loop, hoisted
     * VarHandle, single-strided access - so C2 auto-vectorizes the byteswap. Caller is responsible
     * for bounds checking - this method assumes {@code dstOffset + (count << 2) <= dst.length} and
     * {@code srcOffset + count <= src.length}.</p>
     *
     * @param src source {@code int[]}
     * @param srcOffset element offset into {@code src} of the first element
     * @param dst destination byte array
     * @param dstOffset byte offset into {@code dst} of the first element
     * @param count number of 32-bit integers to encode
     */
    public static void putIntArrayBE(int[] src, int srcOffset, byte[] dst, int dstOffset, int count) {
        for (int i = 0; i < count; i++)
            INT_BE.set(dst, dstOffset + (i << 2), src[srcOffset + i]);
    }

    /**
     * Encodes {@code count} 64-bit integers from {@code src} starting at {@code srcOffset} into
     * {@code dst} as big-endian bytes starting at {@code dstOffset}.
     *
     * <p>Shape matches {@link #getLongArrayBE(byte[], int, long[], int, int)}. Caller is
     * responsible for bounds checking.</p>
     *
     * @param src source {@code long[]}
     * @param srcOffset element offset into {@code src} of the first element
     * @param dst destination byte array
     * @param dstOffset byte offset into {@code dst} of the first element
     * @param count number of 64-bit integers to encode
     */
    public static void putLongArrayBE(long[] src, int srcOffset, byte[] dst, int dstOffset, int count) {
        for (int i = 0; i < count; i++)
            LONG_BE.set(dst, dstOffset + (i << 3), src[srcOffset + i]);
    }

}
