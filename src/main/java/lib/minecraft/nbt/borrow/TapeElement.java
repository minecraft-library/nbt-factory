package lib.minecraft.nbt.borrow;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Static helpers for packing and unpacking {@link Tape#elements} {@code long} entries.
 *
 * <p>The tape's bit layout:</p>
 * <ul>
 *   <li>Bits {@code 56..63} - {@link TapeKind} ordinal (one byte; all current kinds fit).</li>
 *   <li>Bits {@code 0..55} - kind-dependent payload.</li>
 * </ul>
 *
 * <p>For inline primitives ({@code BYTE_INLINE}, {@code SHORT_INLINE}, {@code INT_INLINE},
 * {@code FLOAT_INLINE}) the payload bits hold the value directly. Signed primitives are
 * sign-extended into the {@code long} so {@code (byte) unpackValue} / {@code (short) unpackValue}
 * / {@code (int) unpackValue} recovers the signed value with no extra masking. For pointer kinds
 * ({@code *_PTR}) the payload holds a 32-bit unsigned byte offset into the retained buffer.</p>
 *
 * <p>For {@code COMPOUND_HEADER} the 56-bit payload splits as
 * {@code [reserved:8][approxLen:24][endTapeOffset:24]}. For {@code LIST_HEADER} the same field
 * layout is reused with the {@code reserved} byte repurposed to hold the wire {@code elementId}
 * (NBT type id of the list's elements) so a round trip preserves the element type even on empty
 * lists - simdnbt sidesteps this by giving each element type its own {@code TapeTagKind}
 * (e.g. {@code ByteList}, {@code CompoundList}); we use a single {@code LIST_HEADER} with the
 * type stashed in the high payload byte instead. {@code endTapeOffset} fits in 24 bits since the
 * tape is itself bounded by buffer size: 16M entries == ~128 MB of tape, well past anything a
 * real NBT payload reaches.</p>
 *
 * <p>{@code TapeKind.values()} is cached in {@link #BY_ORDINAL} so {@link #unpackKind(long)} does
 * not pay the array-clone tax that {@code TapeKind.values()} performs on every call.</p>
 */
@ApiStatus.Experimental
@UtilityClass
public class TapeElement {

    /**
     * Cached snapshot of {@link TapeKind#values()} indexed by ordinal. Avoids the per-call array
     * clone that {@code TapeKind.values()} performs.
     */
    static final TapeKind @NotNull [] BY_ORDINAL = TapeKind.values();

    /**
     * Maximum value the {@code approxLen} field of a {@code COMPOUND_HEADER} / {@code LIST_HEADER}
     * can hold. Sizes above this saturate to this value, mirroring simdnbt's clamp on the same
     * field (its {@code 0xff_ffff} cap before this port narrowed the off-end-pointer to 24 bits).
     */
    public static final int MAX_APPROX_LEN = 0xFF_FFFF;

    /**
     * Maximum tape index a {@code COMPOUND_HEADER} / {@code LIST_HEADER} can address through its
     * {@code endTapeOffset} field. 16,777,215 entries is ~128 MB of tape ({@code long}-per-entry),
     * far past any realistic NBT payload.
     */
    public static final int MAX_END_OFFSET = 0xFF_FFFF;

    /**
     * Mask covering the low 56 bits of a tape element - the kind-dependent payload region.
     */
    static final long VALUE_MASK = 0x00FF_FFFF_FFFF_FFFFL;

    /**
     * Packs a tape element with the given kind and a sign-extended-or-unsigned 56-bit payload.
     *
     * <p>Used by every kind whose payload does not require the split header layout - inline
     * primitives, pointer kinds, and the back-reference {@code *_END} markers. For inline signed
     * primitives the caller passes the signed value as a {@code long}; the low 56 bits are
     * preserved verbatim, so a subsequent narrowing cast recovers the original signed value.</p>
     *
     * @param kind the tape kind discriminant
     * @param value the kind-dependent 56-bit payload (signed or unsigned per the kind contract)
     * @return the packed {@code long} suitable for storage in {@link Tape#elements}
     */
    public static long pack(@NotNull TapeKind kind, long value) {
        return ((long) kind.ordinal() << 56) | (value & VALUE_MASK);
    }

    /**
     * Packs a {@link TapeKind#COMPOUND_HEADER} element with the given approximate length and the
     * tape index of the matching {@link TapeKind#COMPOUND_END}. The 8-bit element-type slot is
     * reserved (zeroed) - compound headers do not need it.
     *
     * @param approxLen the approximate child count, saturated at {@link #MAX_APPROX_LEN}
     * @param endTapeOffset the tape index of the matching {@code COMPOUND_END} entry, capped at
     *     {@link #MAX_END_OFFSET}
     * @return the packed {@code long} suitable for storage in {@link Tape#elements}
     * @throws IllegalArgumentException if {@code approxLen} or {@code endTapeOffset} is negative
     *     or if {@code endTapeOffset} exceeds {@link #MAX_END_OFFSET}
     */
    public static long packCompoundHeader(int approxLen, int endTapeOffset) {
        return packHeader(TapeKind.COMPOUND_HEADER, (byte) 0, approxLen, endTapeOffset);
    }

    /**
     * Packs a {@link TapeKind#LIST_HEADER} element with the wire element-id, the approximate
     * length, and the tape index of the matching {@link TapeKind#LIST_END}.
     *
     * <p>The element id is the NBT tag id all entries of this list carry on the wire (e.g.
     * {@code 1} for byte lists, {@code 10} for compound lists). Stashing it in the header lets a
     * round trip preserve the type even on empty lists, where the element type would otherwise
     * be unrecoverable from the absent children.</p>
     *
     * @param elementId the NBT tag id of the list's elements (the byte that immediately follows
     *     the {@code TAG_List} marker on the wire)
     * @param approxLen the approximate element count, saturated at {@link #MAX_APPROX_LEN}
     * @param endTapeOffset the tape index of the matching {@code LIST_END} entry, capped at
     *     {@link #MAX_END_OFFSET}
     * @return the packed {@code long} suitable for storage in {@link Tape#elements}
     * @throws IllegalArgumentException if {@code approxLen} or {@code endTapeOffset} is negative
     *     or if {@code endTapeOffset} exceeds {@link #MAX_END_OFFSET}
     */
    public static long packListHeader(byte elementId, int approxLen, int endTapeOffset) {
        return packHeader(TapeKind.LIST_HEADER, elementId, approxLen, endTapeOffset);
    }

    private static long packHeader(@NotNull TapeKind kind, byte elementId, int approxLen, int endTapeOffset) {
        if (approxLen < 0)
            throw new IllegalArgumentException("approxLen must be non-negative: " + approxLen);

        if (endTapeOffset < 0)
            throw new IllegalArgumentException("endTapeOffset must be non-negative: " + endTapeOffset);

        if (endTapeOffset > MAX_END_OFFSET)
            throw new IllegalArgumentException("endTapeOffset exceeds 24-bit cap: " + endTapeOffset);

        int saturatedLen = Math.min(approxLen, MAX_APPROX_LEN);
        long payload = ((long) (elementId & 0xFF) << 48)
            | ((long) saturatedLen << 24)
            | (endTapeOffset & 0xFF_FFFFL);
        return ((long) kind.ordinal() << 56) | payload;
    }

    /**
     * Returns the {@link TapeKind} discriminant of a packed element. Reads the top byte and
     * indexes {@link #BY_ORDINAL} to avoid the {@code TapeKind.values()} clone.
     *
     * @param element the packed tape element
     * @return the discriminant kind
     */
    public static @NotNull TapeKind unpackKind(long element) {
        return BY_ORDINAL[(int) (element >>> 56)];
    }

    /**
     * Returns the low 56 bits of a packed element as a sign-extended {@code long}.
     *
     * <p>For inline signed primitives the caller narrows directly via {@code (byte)} /
     * {@code (short)} / {@code (int)} and the high bits are discarded. For pointer kinds the
     * caller narrows via {@code (int)} to recover the 32-bit buffer offset. For
     * {@code FLOAT_INLINE} the caller passes the result of {@code (int) unpackValue} to
     * {@link Float#intBitsToFloat(int)}.</p>
     *
     * @param element the packed tape element
     * @return the 56-bit payload sign-extended into a {@code long} via the inline encoding
     */
    public static long unpackValue(long element) {
        // Sign-extend from bit 55 so signed inline primitives narrow correctly. Pointer kinds
        // never set bit 55 in practice (32-bit offsets), so the sign extension is a no-op for
        // them - the (int) narrowing recovers the unsigned offset either way.
        return (element << 8) >> 8;
    }

    /**
     * Returns the {@code approxLen} field of a {@code COMPOUND_HEADER} or {@code LIST_HEADER}
     * element.
     *
     * @param element the packed header element
     * @return the saturated approximate child count
     */
    public static int unpackApproxLen(long element) {
        return (int) ((element >>> 24) & MAX_APPROX_LEN);
    }

    /**
     * Returns the {@code endTapeOffset} field of a {@code COMPOUND_HEADER} or {@code LIST_HEADER}
     * element - the tape index of the matching {@code *_END} marker.
     *
     * <p>Also valid for the {@code *_END} markers themselves, where the low 24 bits carry the
     * back-reference to the originating header's tape index.</p>
     *
     * @param element the packed header (or end-marker) element
     * @return the matched-marker tape index
     */
    public static int unpackEndOffset(long element) {
        return (int) (element & MAX_END_OFFSET);
    }

    /**
     * Returns the wire element-id stashed in a {@code LIST_HEADER}'s 8-bit reserved slot.
     *
     * @param element the packed list-header element
     * @return the NBT tag id of the list's elements (e.g. {@code 1} for byte lists, {@code 10}
     *     for compound lists). Returns {@code 0} for compound headers, where the slot is reserved
     *     and unused.
     */
    public static byte unpackListElementId(long element) {
        return (byte) ((element >>> 48) & 0xFF);
    }

}
