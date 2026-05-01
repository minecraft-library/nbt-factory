package lib.minecraft.nbt;

import lib.minecraft.nbt.io.NbtByteCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Pins {@link NbtByteCodec#getIntArrayBE} / {@link NbtByteCodec#getLongArrayBE} (and the matching
 * encoders) at every length C2's auto-vectorizer treats specially. The peeling boundaries are
 * vector-width aligned (typically 4 or 8 elements per AVX2 lane); a clean run requires the
 * prologue and epilogue scalar paths to round-trip identically to a hand-rolled per-element loop.
 *
 * <p>{@code 0} covers the no-op path; {@code 1..7} cover entirely-scalar runs that never hit the
 * vector body; {@code 8, 16} cover exactly-aligned runs; {@code 15, 17} cover one-short and
 * one-over runs that exercise the epilogue tail.</p>
 */
@DisplayName("NbtByteCodec bulk array byteswap - peeling boundary edge cases")
class IntArrayEdgeCaseTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 7, 8, 15, 16, 17})
    void intArray_lengthRoundTripsCleanly(int length) {
        int[] source = new int[length];
        Random random = new Random(0x12345L + length);

        for (int i = 0; i < length; i++)
            source[i] = random.nextInt();

        byte[] encoded = new byte[length << 2];
        NbtByteCodec.putIntArrayBE(source, 0, encoded, 0, length);

        int[] decoded = new int[length];
        NbtByteCodec.getIntArrayBE(encoded, 0, decoded, 0, length);
        assertThat("length=" + length, decoded, equalTo(source));

        // Equivalence with the per-element path.
        byte[] perElement = new byte[length << 2];
        for (int i = 0; i < length; i++)
            NbtByteCodec.putInt(perElement, i << 2, source[i]);
        assertThat("length=" + length + " encode parity", encoded, equalTo(perElement));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 7, 8, 15, 16, 17})
    void longArray_lengthRoundTripsCleanly(int length) {
        long[] source = new long[length];
        Random random = new Random(0x67890L + length);

        for (int i = 0; i < length; i++)
            source[i] = random.nextLong();

        byte[] encoded = new byte[length << 3];
        NbtByteCodec.putLongArrayBE(source, 0, encoded, 0, length);

        long[] decoded = new long[length];
        NbtByteCodec.getLongArrayBE(encoded, 0, decoded, 0, length);
        assertThat("length=" + length, decoded, equalTo(source));

        byte[] perElement = new byte[length << 3];
        for (int i = 0; i < length; i++)
            NbtByteCodec.putLong(perElement, i << 3, source[i]);
        assertThat("length=" + length + " encode parity", encoded, equalTo(perElement));
    }

}
