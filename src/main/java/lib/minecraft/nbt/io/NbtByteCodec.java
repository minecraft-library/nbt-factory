package dev.sbs.minecraftapi.nbt.io;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/**
 * Static codec for big-endian primitive reads and writes against a raw {@code byte[]}.
 *
 * <p>Java Edition NBT is always big-endian on the wire, so every primitive access in the
 * byte-array backends ({@link dev.sbs.minecraftapi.nbt.io.buffer.NbtInputBuffer NbtInputBuffer}
 * and {@link dev.sbs.minecraftapi.nbt.io.buffer.NbtOutputBuffer NbtOutputBuffer}) flows through
 * this class. The bulk primitive-array reads on the streaming backends
 * ({@link dev.sbs.minecraftapi.nbt.io.stream.NbtInputStream NbtInputStream},
 * {@link dev.sbs.minecraftapi.nbt.io.stream.NbtOutputStream NbtOutputStream}) also use it for
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
public final class NbtByteCodec {

    private static final VarHandle SHORT_BE = MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.BIG_ENDIAN);
    private static final VarHandle INT_BE = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);
    private static final VarHandle LONG_BE = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);

    private NbtByteCodec() {
        throw new UnsupportedOperationException();
    }

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

}
