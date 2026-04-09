package dev.sbs.minecraftapi.nbt.io;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.io.UTFDataFormatException;
import java.nio.charset.StandardCharsets;

/**
 * Modified UTF-8 codec matching {@link java.io.DataInput#readUTF()} and
 * {@link java.io.DataOutput#writeUTF(String)} - the wire format Mojang uses for every string in
 * Minecraft's binary NBT payload.
 *
 * <p>Every NBT string (both compound keys and {@code TAG_String} values) is framed on the wire
 * as a 2-byte big-endian unsigned length prefix followed by {@code length} bytes of modified
 * UTF-8 data, per the <a href="https://minecraft.wiki/w/NBT_format">Minecraft Wiki NBT format</a>
 * specification. This class owns the inner codec; the length-prefix framing lives in the
 * individual backends ({@link dev.sbs.minecraftapi.nbt.io.buffer.NbtInputBuffer NbtInputBuffer},
 * {@link dev.sbs.minecraftapi.nbt.io.buffer.NbtOutputBuffer NbtOutputBuffer}).</p>
 *
 * <p>Modified UTF-8 matches standard UTF-8 for code points in {@code [0x0001..0x007F]}. Two
 * differences from standard UTF-8:</p>
 * <ul>
 *   <li>{@code U+0000} is encoded as two bytes {@code 0xC0 0x80} instead of the single byte
 *       {@code 0x00} - so the byte {@code 0x00} never appears inside a valid encoded string.</li>
 *   <li>Supplementary code points (above the BMP) are encoded as their UTF-16 surrogate pair,
 *       each surrogate in the three-byte form - total six bytes instead of standard UTF-8's
 *       four-byte form.</li>
 * </ul>
 *
 * <p>This class is used by the byte-array NBT backends to match the
 * {@link java.io.DataInputStream}-derived stream backends byte-for-byte. The prior
 * standard-UTF-8 implementation diverged on {@code U+0000} and supplementary characters, which
 * would corrupt a round trip through a real Mojang-written {@code .dat} file containing those
 * code points.</p>
 */
@UtilityClass
public final class NbtModifiedUtf8 {

    /**
     * Returns the number of modified UTF-8 bytes required to encode {@code value}.
     */
    public static int encodedLength(@NotNull String value) {
        int strLen = value.length();
        int byteLen = 0;

        for (int i = 0; i < strLen; i++) {
            char c = value.charAt(i);

            if (c >= 0x0001 && c <= 0x007F)
                byteLen++;
            else if (c > 0x07FF)
                byteLen += 3;
            else
                byteLen += 2;
        }

        return byteLen;
    }

    /**
     * Encodes {@code value} into {@code dst} starting at {@code offset} without a length prefix.
     * The caller is responsible for writing the two-byte big-endian length prefix separately, and
     * for calling {@link #encodedLength(String)} first to size the destination buffer.
     *
     * @return the number of bytes written
     */
    public static int encode(@NotNull String value, byte[] dst, int offset) {
        int strLen = value.length();
        int p = offset;

        for (int i = 0; i < strLen; i++) {
            char c = value.charAt(i);

            if (c >= 0x0001 && c <= 0x007F) {
                dst[p++] = (byte) c;
            } else if (c > 0x07FF) {
                dst[p++] = (byte) (0xE0 | ((c >> 12) & 0x0F));
                dst[p++] = (byte) (0x80 | ((c >> 6) & 0x3F));
                dst[p++] = (byte) (0x80 | (c & 0x3F));
            } else {
                dst[p++] = (byte) (0xC0 | ((c >> 6) & 0x1F));
                dst[p++] = (byte) (0x80 | (c & 0x3F));
            }
        }

        return p - offset;
    }

    /**
     * Decodes {@code utfLen} bytes of modified UTF-8 starting at {@code offset} in {@code src}.
     *
     * <p>Leniently accepts a raw {@code 0x00} byte as {@code U+0000} even though strict modified
     * UTF-8 forbids it - this way any payload produced by the previous standard-UTF-8
     * implementation still round-trips cleanly.</p>
     *
     * <p>Fast path: for all-ASCII input (every byte in {@code [0x00..0x7F]}) this delegates to
     * {@code new String(src, offset, utfLen, UTF_8)} which the JDK intrinsifies to a compact
     * Latin-1 {@link String} with zero intermediate {@code char[]} allocation. ASCII bytes are
     * identical under modified UTF-8 and standard UTF-8 (neither emits the forbidden {@code 0x00}
     * byte normally), so the fast path is both correct and matches the baseline allocation
     * profile byte-for-byte for the common case. The slow path - a manual modified UTF-8 decoder
     * with a scratch {@code char[]} - only runs for inputs containing multi-byte sequences (BMP
     * non-Latin characters, supplementary code points via surrogate pairs, or the
     * modified-UTF-8-specific {@code C0 80} encoding of U+0000).</p>
     *
     * @throws UTFDataFormatException if the bytes are not a valid modified UTF-8 sequence
     */
    public static @NotNull String decode(byte[] src, int offset, int utfLen) throws UTFDataFormatException {
        // ASCII scan. Any high bit set means a multi-byte sequence and triggers the slow path.
        int end = offset + utfLen;
        for (int i = offset; i < end; i++) {
            if ((src[i] & 0x80) != 0)
                return decodeSlow(src, offset, utfLen);
        }

        // All-ASCII: JDK intrinsic decoder produces a compact Latin-1 String with no scratch
        // char[] allocation on the heap.
        return new String(src, offset, utfLen, StandardCharsets.UTF_8);
    }

    private static @NotNull String decodeSlow(byte[] src, int offset, int utfLen) throws UTFDataFormatException {
        // Upper bound: every byte produces at most one char (1-byte ASCII case).
        char[] chars = new char[utfLen];
        int count = offset;
        int end = offset + utfLen;
        int charsLen = 0;

        while (count < end) {
            int c = src[count] & 0xFF;

            switch (c >> 4) {
                case 0, 1, 2, 3, 4, 5, 6, 7 -> {
                    // 1-byte: 0xxxxxxx
                    chars[charsLen++] = (char) c;
                    count++;
                }
                case 12, 13 -> {
                    // 2-byte: 110xxxxx 10xxxxxx
                    count += 2;

                    if (count > end)
                        throw new UTFDataFormatException("malformed modified UTF-8: partial character at end");

                    int char2 = src[count - 1];

                    if ((char2 & 0xC0) != 0x80)
                        throw new UTFDataFormatException("malformed modified UTF-8 around byte " + count);

                    chars[charsLen++] = (char) (((c & 0x1F) << 6) | (char2 & 0x3F));
                }
                case 14 -> {
                    // 3-byte: 1110xxxx 10xxxxxx 10xxxxxx
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
