package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.exception.NbtException;
import lib.minecraft.nbt.io.NbtByteCodec;
import lib.minecraft.nbt.io.NbtModifiedUtf8;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.UTFDataFormatException;

/**
 * Zero-copy view over a modified-UTF-8 byte slice inside a retained tape buffer.
 *
 * <p>Holds {@code (byte[] buffer, int offset, int length)} where {@code offset} addresses the
 * <b>first byte of the payload</b> (not the 2-byte length prefix - that is consumed by
 * {@link #fromTagOffset(byte[], int)}). Decoding to a {@link String} is deferred until
 * {@link #toString()} or {@link #asString()} is called and the result is cached on the instance,
 * so subsequent calls return the same reference.</p>
 *
 * <p>{@link #equalsString(String)} and {@link #hashCode()} take an ASCII fast path via
 * {@link NbtModifiedUtf8#isPlainAscii(byte[], int, int)} - when every payload byte has its high
 * bit clear they compare or hash the bytes directly with no decode allocation. ASCII byte values
 * are bit-identical to their {@code char} counterparts in {@code [0x00..0x7F]}, so the same
 * arithmetic that {@code String.hashCode()} performs on the {@code char[]} produces the same hash
 * when applied to the bytes.</p>
 *
 * <p>Non-ASCII payloads (BMP multi-byte forms, supplementary surrogate pairs, the
 * modified-UTF-8 {@code C0 80} encoding of {@code U+0000}) fall through to a one-shot decode
 * cached for future calls. The {@code C0 80} case in particular forces the slow path because
 * {@code 0xC0} fails the high-bit probe - critical for hash parity, since the byte-level hash of
 * {@code C0 80} would differ from {@code "\0".hashCode()} (which is {@code 0}).</p>
 *
 * <p>Single-thread by convention. The borrow API does not promise thread-safe traversal, so the
 * {@code cached} / {@code asciiState} fields use plain reads; concurrent first-call decode would
 * race but every observer would see a value satisfying the documented invariants.</p>
 *
 * @see BorrowedStringTag
 * @see Tape#findChildTapeIndex(int, String)
 */
@ApiStatus.Experimental
public final class MutfStringView {

    /**
     * Sentinel for {@link #asciiState} - {@link #isAscii()} has not been computed yet.
     */
    private static final byte ASCII_UNKNOWN = 0;

    /**
     * Sentinel for {@link #asciiState} - the payload contains at least one byte with the high
     * bit set.
     */
    private static final byte ASCII_FALSE = 1;

    /**
     * Sentinel for {@link #asciiState} - every payload byte has its high bit clear.
     */
    private static final byte ASCII_TRUE = 2;

    private final byte @NotNull [] buffer;

    private final int offset;

    private final int length;

    /**
     * Cached decoded form. Stays {@code null} until the first {@link #toString()} call. Populated
     * eagerly by {@link #equalsString(String)} on a non-ASCII fall-through and by
     * {@link #hashCode()} on a non-ASCII fall-through.
     */
    private @Nullable String cached;

    /**
     * Tri-state cache for {@link #isAscii()}. {@code 0} = unknown, {@code 1} = non-ASCII,
     * {@code 2} = ASCII. Avoids re-running the 8-byte probe on every {@link #equalsString(String)}
     * / {@link #hashCode()} call.
     */
    private byte asciiState;

    /**
     * Constructs a view over the given byte range. The caller has already stripped the 2-byte
     * length prefix - {@code offset} addresses the first payload byte.
     *
     * @param buffer the retained tape buffer
     * @param offset byte offset of the first payload byte (after the length prefix)
     * @param length payload byte count
     * @throws IllegalArgumentException if {@code offset} or {@code length} is negative or if
     *     {@code offset + length} exceeds {@code buffer.length}
     */
    public MutfStringView(byte @NotNull [] buffer, int offset, int length) {
        if (offset < 0)
            throw new IllegalArgumentException("offset must be non-negative: " + offset);

        if (length < 0)
            throw new IllegalArgumentException("length must be non-negative: " + length);

        if ((long) offset + (long) length > buffer.length)
            throw new IllegalArgumentException(
                "view range [" + offset + ", " + (offset + length) + ") exceeds buffer length " + buffer.length);

        this.buffer = buffer;
        this.offset = offset;
        this.length = length;
    }

    /**
     * Constructs a view over a length-prefixed modified-UTF-8 string at {@code tagOffset} -
     * the offset addresses the 2-byte big-endian length prefix and the returned view points at
     * the payload bytes that immediately follow.
     *
     * <p>This is the call site for {@link Tape#findChildTapeIndex(int, String)} and
     * {@link BorrowedStringTag} - both kinds of tape pointer (key and string-value) address the
     * length prefix on the wire.</p>
     *
     * @param buffer the retained tape buffer
     * @param tagOffset byte offset of the 2-byte big-endian length prefix
     * @return a view over the payload bytes
     */
    public static @NotNull MutfStringView fromTagOffset(byte @NotNull [] buffer, int tagOffset) {
        int len = NbtByteCodec.getUnsignedShort(buffer, tagOffset);
        return new MutfStringView(buffer, tagOffset + 2, len);
    }

    /**
     * Returns the modified-UTF-8 payload byte count - <b>not</b> the decoded character count.
     *
     * @return payload byte length
     */
    public int byteLength() {
        return this.length;
    }

    /**
     * True when the payload is empty.
     *
     * @return whether the view has zero payload bytes
     */
    public boolean isEmpty() {
        return this.length == 0;
    }

    /**
     * True when every payload byte has its high bit clear (i.e., is in {@code [0x00..0x7F]}).
     *
     * <p>Caches the result on the instance so subsequent calls return without re-running the
     * 8-byte probe. Note that the modified-UTF-8 {@code C0 80} encoding of {@code U+0000} fails
     * this check because {@code 0xC0} has the high bit set - so a view holding
     * {@code "\0"}-encoded bytes correctly takes the non-ASCII path in
     * {@link #equalsString(String)} and {@link #hashCode()}.</p>
     *
     * @return whether the underlying byte slice is plain ASCII
     */
    public boolean isAscii() {
        byte state = this.asciiState;

        if (state != ASCII_UNKNOWN)
            return state == ASCII_TRUE;

        boolean ascii = NbtModifiedUtf8.isPlainAscii(this.buffer, this.offset, this.length);
        this.asciiState = ascii ? ASCII_TRUE : ASCII_FALSE;
        return ascii;
    }

    /**
     * Returns the decoded {@link String}, decoding lazily on first call and caching the result.
     *
     * <p>Routes through {@link NbtModifiedUtf8#decode(byte[], int, int)} so the ASCII probe and
     * compact-Latin-1 fast path inside the codec apply here too. Wraps the checked
     * {@link UTFDataFormatException} as {@link NbtException} - corruption here means the retained
     * buffer was tampered with, not user input.</p>
     *
     * @return the decoded string
     * @throws NbtException if the underlying bytes are not a valid modified-UTF-8 sequence
     */
    @Override
    public @NotNull String toString() {
        String cached = this.cached;

        if (cached != null)
            return cached;

        try {
            String decoded = NbtModifiedUtf8.decode(this.buffer, this.offset, this.length);
            this.cached = decoded;
            return decoded;
        } catch (UTFDataFormatException exception) {
            throw new NbtException(
                exception, "Malformed modified UTF-8 in tape buffer at offset %d", this.offset);
        }
    }

    /**
     * Alias for {@link #toString()} - explicit "force decode" entry point so callers reading the
     * view as a payload (rather than as a debug rendering) read at the right level of intent.
     *
     * @return the decoded string
     * @throws NbtException if the underlying bytes are not a valid modified-UTF-8 sequence
     */
    public @NotNull String asString() {
        return this.toString();
    }

    /**
     * Byte-level equality against another view. Two views are equal when their payload byte
     * ranges are identical - decode is never invoked.
     *
     * @param other the other view (may be {@code null})
     * @return whether both views address byte-identical payloads
     */
    public boolean equals(@Nullable MutfStringView other) {
        if (other == null)
            return false;

        if (other == this)
            return true;

        if (this.length != other.length)
            return false;

        for (int i = 0; i < this.length; i++) {
            if (this.buffer[this.offset + i] != other.buffer[other.offset + i])
                return false;
        }

        return true;
    }

    /**
     * Equality against an arbitrary {@link Object}. Returns {@code true} only for another
     * {@link MutfStringView} addressing a byte-identical payload - never compares against a
     * {@link String}, since the {@code Object.equals} contract requires symmetry and
     * {@link String#equals(Object)} would not return {@code true} for a {@code MutfStringView}
     * argument. Use {@link #equalsString(String)} for the asymmetric view-vs-string comparison.
     *
     * @param o the other object
     * @return whether {@code o} is a byte-identical {@link MutfStringView}
     */
    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o)
            return true;

        if (!(o instanceof MutfStringView other))
            return false;

        return this.equals(other);
    }

    /**
     * Compares this view's payload against {@code other}'s decoded form without materializing a
     * {@link String} when both sides are ASCII.
     *
     * <p>Algorithm:</p>
     * <ul>
     *   <li>If the view is ASCII, payload byte count equals decoded character count - so a length
     *       mismatch against {@code other.length()} short-circuits to {@code false} immediately,
     *       and otherwise each byte is compared against the corresponding {@code char} (which
     *       has the same numeric value in {@code [0x00..0x7F]}).</li>
     *   <li>If the view is non-ASCII, the byte count and character count differ in general, so
     *       the length pre-check is skipped and the comparison falls through to
     *       {@code toString().equals(other)}. The decoded form is cached on the instance for
     *       future calls.</li>
     * </ul>
     *
     * <p>Result is byte-identical to {@code this.toString().equals(other)} for every input.</p>
     *
     * @param other the {@link String} to compare against (may be {@code null})
     * @return whether the decoded view equals {@code other}
     */
    public boolean equalsString(@Nullable String other) {
        if (other == null)
            return false;

        if (this.isAscii()) {
            // ASCII char count == byte count, so a length mismatch is decisive without decode.
            if (this.length != other.length())
                return false;

            for (int i = 0; i < this.length; i++) {
                // ASCII: byte b in [0x00..0x7F] equals char c in the same range, so comparing
                // (b & 0xFF) against (int) c produces the right answer with no decode.
                if ((this.buffer[this.offset + i] & 0xFF) != other.charAt(i))
                    return false;
            }

            return true;
        }

        return this.toString().equals(other);
    }

    /**
     * Hash matching {@link String#hashCode()} for the decoded form.
     *
     * <p>{@code String.hashCode()} computes
     * {@code s[0] * 31^(n-1) + s[1] * 31^(n-2) + ... + s[n-1]} over the {@code char[]}. ASCII
     * bytes are bit-identical to their {@code char} counterparts, so the same recurrence applied
     * to the bytes produces the same hash with no decode. Non-ASCII payloads (including the
     * {@code C0 80} encoding of {@code U+0000}, where {@code 0xC0} has the high bit set and so
     * fails the ASCII probe) decode and delegate to {@link String#hashCode()} - which for
     * {@code "\0"} returns {@code 0}, while a byte-level hash of {@code C0 80} would return
     * {@code 0xC0 * 31 + 0x80 == 6080}.</p>
     *
     * <p>Parity is locked in by {@code MutfStringViewTest}'s parity matrix.</p>
     *
     * @return hash equal to {@code this.toString().hashCode()}
     */
    @Override
    public int hashCode() {
        if (this.isAscii()) {
            int h = 0;

            for (int i = 0; i < this.length; i++)
                h = 31 * h + (this.buffer[this.offset + i] & 0xFF);

            return h;
        }

        // Forces decode and caches the String for future toString() calls.
        return this.toString().hashCode();
    }

}
