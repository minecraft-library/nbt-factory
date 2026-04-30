package lib.minecraft.nbt;

import lib.minecraft.nbt.io.stream.ArrayReadStrategy;
import lib.minecraft.nbt.io.stream.ArrayWriteStrategy;
import lib.minecraft.nbt.io.stream.NbtInputStream;
import lib.minecraft.nbt.io.stream.NbtOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 * Pins both {@link ArrayReadStrategy} variants and both {@link ArrayWriteStrategy} variants to
 * byte-identical output. Every payload written with one strategy must read back unchanged through
 * either strategy, and the on-wire bytes must match the existing single-buffer implementation.
 *
 * <p>Phase F of the optimization plan picks the JMH winner as the default; this test ensures the
 * loser stays correct so future callers can opt back in.</p>
 */
@DisplayName("Stream array read/write strategies - bit-identical contract")
class ArrayStrategyTest {

    /**
     * Sizes covering the small/medium/large/edge spectrum: empty, single, sub-chunk, on-cap,
     * just-over-cap, and a multi-chunk run that exercises the CHUNKED_THREADLOCAL loop boundary.
     * The 64 KiB scratch holds 16384 ints / 8192 longs - 65536 elements covers the multi-chunk
     * case for both.
     */
    private static final int[] SIZES = {0, 1, 1024, 16383, 16384, 16385, 65536, 65537};

    // ---------------------------------------------------------------------
    // int[] round-trip
    // ---------------------------------------------------------------------

    @ParameterizedTest(name = "intArray write={0}")
    @EnumSource(ArrayWriteStrategy.class)
    @DisplayName("int[] write strategy produces bytes readable by both read strategies")
    void intArray_writeStrategy_matchesAllReadStrategies(ArrayWriteStrategy writeStrategy) throws Exception {
        for (int size : SIZES) {
            int[] expected = buildIntArray(size);
            byte[] payload = writeIntArray(expected, writeStrategy);

            for (ArrayReadStrategy readStrategy : ArrayReadStrategy.values()) {
                int[] actual = readIntArray(payload, readStrategy);
                assertThat("write=" + writeStrategy + " read=" + readStrategy + " size=" + size,
                    actual, is(equalTo(expected)));
            }
        }
    }

    @ParameterizedTest(name = "intArray read={0}")
    @EnumSource(ArrayReadStrategy.class)
    @DisplayName("int[] read strategy decodes payloads written by both write strategies identically")
    void intArray_readStrategy_decodesAllWriteStrategies(ArrayReadStrategy readStrategy) throws Exception {
        for (int size : SIZES) {
            int[] expected = buildIntArray(size);

            byte[] streamPayload = writeIntArray(expected, ArrayWriteStrategy.STREAMWISE);
            byte[] chunkedPayload = writeIntArray(expected, ArrayWriteStrategy.CHUNKED_THREADLOCAL);

            // Bit-identical write outputs.
            assertThat("write outputs differ at size=" + size,
                chunkedPayload, is(equalTo(streamPayload)));

            int[] actual = readIntArray(streamPayload, readStrategy);
            assertThat("read=" + readStrategy + " size=" + size,
                actual, is(equalTo(expected)));
        }
    }

    // ---------------------------------------------------------------------
    // long[] round-trip
    // ---------------------------------------------------------------------

    @ParameterizedTest(name = "longArray write={0}")
    @EnumSource(ArrayWriteStrategy.class)
    @DisplayName("long[] write strategy produces bytes readable by both read strategies")
    void longArray_writeStrategy_matchesAllReadStrategies(ArrayWriteStrategy writeStrategy) throws Exception {
        for (int size : SIZES) {
            long[] expected = buildLongArray(size);
            byte[] payload = writeLongArray(expected, writeStrategy);

            for (ArrayReadStrategy readStrategy : ArrayReadStrategy.values()) {
                long[] actual = readLongArray(payload, readStrategy);
                assertThat("write=" + writeStrategy + " read=" + readStrategy + " size=" + size,
                    actual, is(equalTo(expected)));
            }
        }
    }

    @ParameterizedTest(name = "longArray read={0}")
    @EnumSource(ArrayReadStrategy.class)
    @DisplayName("long[] read strategy decodes payloads written by both write strategies identically")
    void longArray_readStrategy_decodesAllWriteStrategies(ArrayReadStrategy readStrategy) throws Exception {
        for (int size : SIZES) {
            long[] expected = buildLongArray(size);

            byte[] streamPayload = writeLongArray(expected, ArrayWriteStrategy.STREAMWISE);
            byte[] chunkedPayload = writeLongArray(expected, ArrayWriteStrategy.CHUNKED_THREADLOCAL);

            assertThat("write outputs differ at size=" + size,
                chunkedPayload, is(equalTo(streamPayload)));

            long[] actual = readLongArray(streamPayload, readStrategy);
            assertThat("read=" + readStrategy + " size=" + size,
                actual, is(equalTo(expected)));
        }
    }

    // ---------------------------------------------------------------------
    // Mixed primitive interleave - guards against scratch-buffer pollution between calls.
    // ---------------------------------------------------------------------

    @ParameterizedTest(name = "mixed write={0}")
    @EnumSource(ArrayWriteStrategy.class)
    @DisplayName("mixed int/long interleave round-trips byte-for-byte across strategies")
    void mixedInterleave_roundTrips(ArrayWriteStrategy writeStrategy) throws Exception {
        int[] ints1 = buildIntArray(17000);
        long[] longs1 = buildLongArray(9000);
        int[] ints2 = buildIntArray(3);
        long[] longs2 = buildLongArray(0);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (NbtOutputStream out = new NbtOutputStream(baos, writeStrategy)) {
            out.writeIntArray(ints1);
            out.writeLongArray(longs1);
            out.writeIntArray(ints2);
            out.writeLongArray(longs2);
        }

        byte[] payload = baos.toByteArray();

        for (ArrayReadStrategy readStrategy : ArrayReadStrategy.values()) {
            try (NbtInputStream in = new NbtInputStream(new ByteArrayInputStream(payload), readStrategy)) {
                assertThat("write=" + writeStrategy + " read=" + readStrategy + " ints1",
                    in.readIntArray(), is(equalTo(ints1)));
                assertThat("write=" + writeStrategy + " read=" + readStrategy + " longs1",
                    in.readLongArray(), is(equalTo(longs1)));
                assertThat("write=" + writeStrategy + " read=" + readStrategy + " ints2",
                    in.readIntArray(), is(equalTo(ints2)));
                assertThat("write=" + writeStrategy + " read=" + readStrategy + " longs2",
                    in.readLongArray(), is(equalTo(longs2)));
            }
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static int[] buildIntArray(int size) {
        // Deterministic, covers full 32-bit range.
        Random r = new Random(0xCAFEBABE ^ size);
        int[] data = new int[size];
        for (int i = 0; i < size; i++) data[i] = r.nextInt();
        if (size > 0) data[0] = Integer.MIN_VALUE;
        if (size > 1) data[size - 1] = Integer.MAX_VALUE;
        return data;
    }

    private static long[] buildLongArray(int size) {
        Random r = new Random(0xDEADBEEFL ^ size);
        long[] data = new long[size];
        for (int i = 0; i < size; i++) data[i] = r.nextLong();
        if (size > 0) data[0] = Long.MIN_VALUE;
        if (size > 1) data[size - 1] = Long.MAX_VALUE;
        return data;
    }

    private static byte[] writeIntArray(int[] data, ArrayWriteStrategy strategy) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (NbtOutputStream out = new NbtOutputStream(baos, strategy)) {
            out.writeIntArray(data);
        }
        return baos.toByteArray();
    }

    private static byte[] writeLongArray(long[] data, ArrayWriteStrategy strategy) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (NbtOutputStream out = new NbtOutputStream(baos, strategy)) {
            out.writeLongArray(data);
        }
        return baos.toByteArray();
    }

    private static int[] readIntArray(byte[] payload, ArrayReadStrategy strategy) throws Exception {
        try (NbtInputStream in = new NbtInputStream(new ByteArrayInputStream(payload), strategy)) {
            return in.readIntArray();
        }
    }

    private static long[] readLongArray(byte[] payload, ArrayReadStrategy strategy) throws Exception {
        try (NbtInputStream in = new NbtInputStream(new ByteArrayInputStream(payload), strategy)) {
            return in.readLongArray();
        }
    }

}
