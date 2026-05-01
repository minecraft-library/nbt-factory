package lib.minecraft.nbt;

import lib.minecraft.nbt.io.NbtByteCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 * Pins the bulk byteswap codec ({@link NbtByteCodec#getIntArrayBE}, {@link NbtByteCodec#getLongArrayBE},
 * {@link NbtByteCodec#putIntArrayBE}, {@link NbtByteCodec#putLongArrayBE}) to bit-identical output
 * versus the per-element {@link NbtByteCodec#getInt} / {@link NbtByteCodec#getLong} path.
 *
 * <p>Sized at 1023 ints to match {@code simdnbt-corpus/inttest1023.nbt} - the dominant payload
 * Phase B1 targets - and 1024 longs as the natural counterpart. Equivalence with the per-element
 * path is the hard contract; performance is measured separately under JMH.</p>
 */
@DisplayName("NbtByteCodec bulk array byteswap - bit-identical contract")
class IntArrayCodecTest {

    @Test
    void intArray1023_roundTrips_byteIdentical() {
        int[] source = new int[1023];
        Random random = new Random(0xCAFEBABE);

        for (int i = 0; i < source.length; i++)
            source[i] = random.nextInt();

        // Encode through the bulk path.
        byte[] bulk = new byte[source.length << 2];
        NbtByteCodec.putIntArrayBE(source, 0, bulk, 0, source.length);

        // Encode through the per-element path for comparison.
        byte[] perElement = new byte[source.length << 2];
        for (int i = 0; i < source.length; i++)
            NbtByteCodec.putInt(perElement, i << 2, source[i]);

        assertThat("bulk encode must match per-element encode byte-for-byte", bulk, equalTo(perElement));

        // Decode through the bulk path.
        int[] decoded = new int[source.length];
        NbtByteCodec.getIntArrayBE(bulk, 0, decoded, 0, source.length);
        assertThat("bulk decode must round-trip the original ints", decoded, equalTo(source));

        // Decode through the per-element path for comparison.
        int[] decodedPerElement = new int[source.length];
        for (int i = 0; i < source.length; i++)
            decodedPerElement[i] = NbtByteCodec.getInt(bulk, i << 2);

        assertThat("bulk decode must match per-element decode value-for-value",
                decoded, equalTo(decodedPerElement));
    }

    @Test
    void longArray1024_roundTrips_byteIdentical() {
        long[] source = new long[1024];
        Random random = new Random(0xDEADBEEFL);

        for (int i = 0; i < source.length; i++)
            source[i] = random.nextLong();

        byte[] bulk = new byte[source.length << 3];
        NbtByteCodec.putLongArrayBE(source, 0, bulk, 0, source.length);

        byte[] perElement = new byte[source.length << 3];
        for (int i = 0; i < source.length; i++)
            NbtByteCodec.putLong(perElement, i << 3, source[i]);

        assertThat("bulk encode must match per-element encode byte-for-byte", bulk, equalTo(perElement));

        long[] decoded = new long[source.length];
        NbtByteCodec.getLongArrayBE(bulk, 0, decoded, 0, source.length);
        assertThat("bulk decode must round-trip the original longs", decoded, equalTo(source));

        long[] decodedPerElement = new long[source.length];
        for (int i = 0; i < source.length; i++)
            decodedPerElement[i] = NbtByteCodec.getLong(bulk, i << 3);

        assertThat("bulk decode must match per-element decode value-for-value",
                decoded, equalTo(decodedPerElement));
    }

    @Test
    void intArray_extremaPattern_preservesSignBits() {
        // Mix MIN_VALUE / MAX_VALUE / 0 / -1 to catch any path that drops the sign bit during
        // encode or decode (a classic byteswap implementation hazard when the upper byte is 0xFF
        // or 0x80).
        int[] source = {Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE, 0x7F00FF80, 0x80FF007F};
        byte[] encoded = new byte[source.length << 2];
        NbtByteCodec.putIntArrayBE(source, 0, encoded, 0, source.length);

        int[] decoded = new int[source.length];
        NbtByteCodec.getIntArrayBE(encoded, 0, decoded, 0, source.length);
        assertThat(decoded, equalTo(source));
    }

    @Test
    void longArray_extremaPattern_preservesSignBits() {
        long[] source = {Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE,
                         0x7F00FF80FF00007FL, 0x80FF007F007FFF00L};
        byte[] encoded = new byte[source.length << 3];
        NbtByteCodec.putLongArrayBE(source, 0, encoded, 0, source.length);

        long[] decoded = new long[source.length];
        NbtByteCodec.getLongArrayBE(encoded, 0, decoded, 0, source.length);
        assertThat(decoded, equalTo(source));
    }

    @Test
    void intArray_dstOffsetIsRespected() {
        int[] source = {1, 2, 3, 4, 5};
        byte[] encoded = new byte[source.length << 2];
        NbtByteCodec.putIntArrayBE(source, 0, encoded, 0, source.length);

        int[] dst = new int[10];
        NbtByteCodec.getIntArrayBE(encoded, 0, dst, 3, source.length);
        assertThat(dst[0], is(0));
        assertThat(dst[1], is(0));
        assertThat(dst[2], is(0));
        assertThat(dst[3], is(1));
        assertThat(dst[7], is(5));
        assertThat(dst[8], is(0));
        assertThat(dst[9], is(0));
    }

    @Test
    void longArray_srcOffsetIsRespected() {
        long[] source = {0L, 0L, 100L, 200L, 300L};
        byte[] encoded = new byte[3 << 3];
        NbtByteCodec.putLongArrayBE(source, 2, encoded, 0, 3);

        long[] decoded = new long[3];
        NbtByteCodec.getLongArrayBE(encoded, 0, decoded, 0, 3);
        assertThat(decoded[0], is(100L));
        assertThat(decoded[1], is(200L));
        assertThat(decoded[2], is(300L));
    }

}
