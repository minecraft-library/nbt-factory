package lib.minecraft.nbt;

import lib.minecraft.nbt.io.NbtModifiedUtf8;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.UTFDataFormatException;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 * Pins {@link NbtModifiedUtf8#decode(byte[], int, int)} to bit-identical output across the
 * 8-byte ASCII probe fast path and the slow multi-byte path.
 *
 * <p>The Phase B2 change replaces a per-byte high-bit scan with an 8-byte chunked probe; this
 * suite exercises every chunk-boundary alignment plus the multi-byte forms (modified-UTF-8
 * 2-byte BMP, 3-byte BMP, 6-byte surrogate-pair supplementary, and the {@code C0 80} encoding
 * of {@code U+0000}). A randomized property test cross-checks the fast path against an
 * always-slow reference for 1000 random byte sequences.</p>
 */
@DisplayName("NbtModifiedUtf8.decode - ASCII probe correctness")
class MutfDecodeTest {

    @Test
    void asciiHundredBytes_roundTrips() throws UTFDataFormatException {
        String input = "the quick brown fox jumps over the lazy dog 0123456789A "
                + "the quick brown fox jumps over the lazy dog!";
        byte[] bytes = input.getBytes(StandardCharsets.US_ASCII);
        assertThat("precondition: 100 bytes ASCII", bytes.length, is(100));

        String decoded = NbtModifiedUtf8.decode(bytes, 0, bytes.length);
        assertThat(decoded, equalTo(input));
    }

    @Test
    void asciiEightBytes_exactChunkBoundary() throws UTFDataFormatException {
        byte[] bytes = "abcdefgh".getBytes(StandardCharsets.US_ASCII);
        assertThat(bytes.length, is(8));

        String decoded = NbtModifiedUtf8.decode(bytes, 0, bytes.length);
        assertThat(decoded, equalTo("abcdefgh"));
    }

    @Test
    void asciiSevenBytes_peelOnlyPath() throws UTFDataFormatException {
        // 7 bytes: 4-byte peel + 2-byte peel + 1-byte peel hits all three tail probes.
        byte[] bytes = "abcdefg".getBytes(StandardCharsets.US_ASCII);
        assertThat(bytes.length, is(7));

        String decoded = NbtModifiedUtf8.decode(bytes, 0, bytes.length);
        assertThat(decoded, equalTo("abcdefg"));
    }

    @Test
    void asciiNineBytes_oneChunkPlusOnePeel() throws UTFDataFormatException {
        // 9 bytes: 1 long chunk + 1 single-byte peel.
        byte[] bytes = "abcdefghi".getBytes(StandardCharsets.US_ASCII);
        assertThat(bytes.length, is(9));

        String decoded = NbtModifiedUtf8.decode(bytes, 0, bytes.length);
        assertThat(decoded, equalTo("abcdefghi"));
    }

    @Test
    void rawZeroByte_acceptedByFastPath() throws UTFDataFormatException {
        // Lenient acceptance: the existing decoder admits a raw 0x00 byte as U+0000 even though
        // strict modified UTF-8 forbids it. The 8-byte probe accepts 0x00 because its high bit
        // is zero; the JDK fast-path String constructor then emits U+0000 directly.
        byte[] bytes = {'a', 'b', 0x00, 'c', 'd'};

        String decoded = NbtModifiedUtf8.decode(bytes, 0, bytes.length);
        assertThat(decoded.length(), is(5));
        assertThat(decoded.charAt(0), is('a'));
        assertThat(decoded.charAt(1), is('b'));
        assertThat(decoded.charAt(2), is('\0'));
        assertThat(decoded.charAt(3), is('c'));
        assertThat(decoded.charAt(4), is('d'));
    }

    @Test
    void modifiedUtf8C080_decodesToU0000() throws UTFDataFormatException {
        // Strict modified-UTF-8 form of U+0000: C0 80. Slow path handles this.
        byte[] bytes = {'a', (byte) 0xC0, (byte) 0x80, 'b'};

        String decoded = NbtModifiedUtf8.decode(bytes, 0, bytes.length);
        assertThat(decoded.length(), is(3));
        assertThat(decoded.charAt(0), is('a'));
        assertThat(decoded.charAt(1), is('\0'));
        assertThat(decoded.charAt(2), is('b'));
    }

    @Test
    void bmpTwoByteChar_eAcute_routesToSlowPath() throws UTFDataFormatException {
        // U+00E9 'é' encodes as C3 A9 in modified UTF-8 (same as standard UTF-8 for BMP < U+0800).
        byte[] bytes = "café".getBytes(StandardCharsets.UTF_8);

        String decoded = NbtModifiedUtf8.decode(bytes, 0, bytes.length);
        assertThat(decoded, equalTo("café"));
    }

    @Test
    void supplementaryCodePoint_emojiRoutesToSlowPath() throws UTFDataFormatException {
        // U+1F600 is a supplementary code point. In modified UTF-8 it encodes as the 6-byte
        // surrogate-pair form: ED A0 BD ED B8 80 (high surrogate D83D + low surrogate DE00).
        // String.getBytes(UTF_8) emits the standard 4-byte form, so we must build the bytes by
        // hand - the slow path is what handles modified UTF-8's surrogate-pair encoding.
        byte[] bytes = {
                (byte) 0xED, (byte) 0xA0, (byte) 0xBD,  // high surrogate U+D83D
                (byte) 0xED, (byte) 0xB8, (byte) 0x80   // low surrogate U+DE00
        };

        String decoded = NbtModifiedUtf8.decode(bytes, 0, bytes.length);
        assertThat(decoded.length(), is(2));
        assertThat(decoded.charAt(0), is((char) 0xD83D));
        assertThat(decoded.charAt(1), is((char) 0xDE00));
        assertThat(decoded.codePointAt(0), is(0x1F600));
    }

    @Test
    void highBitOnlyOnEighthByte_probeSeesIt() throws UTFDataFormatException {
        // Probes the AND-mask correctness: the high bit lives in the last byte of the first
        // 8-byte chunk. If the probe missed any lane, we would incorrectly take the fast path
        // and emit garbage. Building the high-bit byte as a valid 2-byte sequence header would
        // conflict with the next byte, so we use a 2-byte modified-UTF-8 char placed at the
        // tail of an 8-byte window: 7 ASCII + (C2 A0) = 9 bytes, with C2 at byte index 7.
        byte[] bytes = {'a', 'b', 'c', 'd', 'e', 'f', 'g', (byte) 0xC2, (byte) 0xA0};

        String decoded = NbtModifiedUtf8.decode(bytes, 0, bytes.length);
        assertThat(decoded.length(), is(8));
        assertThat(decoded.substring(0, 7), equalTo("abcdefg"));
        assertThat(decoded.charAt(7), is(' '));
    }

    @Test
    void offsetAndLengthAreRespected() throws UTFDataFormatException {
        // ASCII fast path with a non-zero offset and a length shorter than the buffer.
        byte[] bytes = "PADDING_abcdefghij_PADDING".getBytes(StandardCharsets.US_ASCII);
        int offset = 8;     // 'a'
        int length = 10;    // "abcdefghij"

        String decoded = NbtModifiedUtf8.decode(bytes, offset, length);
        assertThat(decoded, equalTo("abcdefghij"));
    }

    @Test
    void emptyInput_returnsEmptyString() throws UTFDataFormatException {
        String decoded = NbtModifiedUtf8.decode(new byte[]{}, 0, 0);
        assertThat(decoded, equalTo(""));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 15, 16, 17, 31, 32, 33, 63, 64, 65, 100, 200, 256})
    void allAsciiAtEveryLength_matchesSlowPath(int len) throws UTFDataFormatException {
        byte[] bytes = new byte[len];
        Random random = new Random(0xC0FFEEL ^ len);

        for (int i = 0; i < len; i++)
            bytes[i] = (byte) random.nextInt(0x80);  // ASCII only.

        String fastResult = NbtModifiedUtf8.decode(bytes, 0, len);
        String slowResult = decodeViaSlowPath(bytes, 0, len);

        assertThat("len=" + len + " fast vs slow", fastResult, equalTo(slowResult));
    }

    @Test
    void randomMixed_matchesSlowPath_propertyTest() throws UTFDataFormatException {
        // Property test: 1000 random byte sequences of length 0..256, ~50% all-ASCII. The
        // decoder's output must equal the always-slow reference path's output for every input.
        Random random = new Random(0xBEEFL);

        for (int trial = 0; trial < 1000; trial++) {
            int len = random.nextInt(257);
            boolean asciiOnly = random.nextBoolean();
            byte[] bytes = new byte[len];

            for (int i = 0; i < len; i++) {
                if (asciiOnly)
                    bytes[i] = (byte) random.nextInt(0x80);
                else
                    bytes[i] = (byte) random.nextInt(0x80);  // start ASCII; we splice multi-byte below.
            }

            // For non-ASCII trials, splice in a valid 2-byte modified-UTF-8 sequence somewhere.
            if (!asciiOnly && len >= 2) {
                int pos = random.nextInt(len - 1);
                // U+00A0..U+07FF range, encoded as C2..DF + 80..BF. Pick a fixed safe pair.
                bytes[pos] = (byte) 0xC2;
                bytes[pos + 1] = (byte) 0xA0;
            }

            String fastResult = NbtModifiedUtf8.decode(bytes, 0, len);
            String slowResult = decodeViaSlowPath(bytes, 0, len);

            assertThat("trial=" + trial + " len=" + len + " ascii=" + asciiOnly,
                    fastResult, equalTo(slowResult));
        }
    }

    /**
     * Reference decoder that bypasses the ASCII fast path so we can cross-check the production
     * decoder. Mirrors the exact logic of {@code NbtModifiedUtf8.decodeSlow} - if both paths
     * agree, we know the new probe has not introduced a divergence.
     */
    private static String decodeViaSlowPath(byte[] src, int offset, int utfLen) throws UTFDataFormatException {
        char[] chars = new char[utfLen];
        int count = offset;
        int end = offset + utfLen;
        int charsLen = 0;

        while (count < end) {
            int c = src[count] & 0xFF;

            switch (c >> 4) {
                case 0, 1, 2, 3, 4, 5, 6, 7 -> {
                    chars[charsLen++] = (char) c;
                    count++;
                }
                case 12, 13 -> {
                    count += 2;

                    if (count > end)
                        throw new UTFDataFormatException("malformed modified UTF-8: partial character at end");

                    int char2 = src[count - 1];

                    if ((char2 & 0xC0) != 0x80)
                        throw new UTFDataFormatException("malformed modified UTF-8 around byte " + count);

                    chars[charsLen++] = (char) (((c & 0x1F) << 6) | (char2 & 0x3F));
                }
                case 14 -> {
                    count += 3;

                    if (count > end)
                        throw new UTFDataFormatException("malformed modified UTF-8: partial character at end");

                    int char2 = src[count - 2];
                    int char3 = src[count - 1];

                    if ((char2 & 0xC0) != 0x80 || (char3 & 0xC0) != 0x80)
                        throw new UTFDataFormatException("malformed modified UTF-8 around byte " + (count - 1));

                    chars[charsLen++] = (char) (((c & 0x0F) << 12) | ((char2 & 0x3F) << 6) | (char3 & 0x3F));
                }
                default -> throw new UTFDataFormatException("malformed modified UTF-8 around byte " + count);
            }
        }

        return new String(chars, 0, charsLen);
    }

}
