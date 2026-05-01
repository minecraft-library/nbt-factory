package lib.minecraft.nbt.borrow;

import org.jetbrains.annotations.ApiStatus;

/**
 * Discriminant for the high 8 bits of a packed tape element {@code long}.
 *
 * <p>Mirrors {@code simdnbt::borrow::tape::TapeTagKind}. The Java port assigns ordinals
 * implicitly (declaration order) so callers must not reorder constants - the ordinal is the wire
 * value packed into the top byte of every {@link TapeElement} {@code long}, and reordering would
 * silently break round-tripping any tape produced by an older version of the library.</p>
 *
 * <p>Each constant documents what the low 56 bits of the tape element carry. The C1 encoder
 * ({@link Tape#encode(lib.minecraft.nbt.tags.collection.CompoundTag) Tape.encode}) and the
 * not-yet-landed C2 streaming parser produce identical tape shapes for the same input.</p>
 */
@ApiStatus.Experimental
public enum TapeKind {

    /**
     * Inline {@code TAG_Byte}. Low 56 bits hold the signed byte sign-extended into a {@code long};
     * read via {@code (byte) TapeElement.unpackValue(el)}.
     */
    BYTE_INLINE,

    /**
     * Inline {@code TAG_Short}. Low 56 bits hold the signed short sign-extended into a
     * {@code long}; read via {@code (short) TapeElement.unpackValue(el)}.
     */
    SHORT_INLINE,

    /**
     * Inline {@code TAG_Int}. Low 56 bits hold the signed int sign-extended into a {@code long};
     * read via {@code (int) TapeElement.unpackValue(el)}.
     */
    INT_INLINE,

    /**
     * Inline {@code TAG_Float}. Low 56 bits hold the IEEE-754 32-bit bit pattern; read via
     * {@code Float.intBitsToFloat((int) TapeElement.unpackValue(el))}.
     */
    FLOAT_INLINE,

    /**
     * Pointer to an 8-byte big-endian {@code TAG_Long} payload in the retained buffer. The 64-bit
     * value does not fit in the 56 low bits of a tape element, so the tape stores the buffer
     * offset and the consumer reads the value via
     * {@link lib.minecraft.nbt.io.NbtByteCodec#getLong(byte[], int) NbtByteCodec.getLong}.
     */
    LONG_PTR,

    /**
     * Pointer to an 8-byte big-endian {@code TAG_Double} payload in the retained buffer. Same
     * rationale as {@link #LONG_PTR}; consumer reads via
     * {@link lib.minecraft.nbt.io.NbtByteCodec#getDouble(byte[], int) NbtByteCodec.getDouble}.
     */
    DOUBLE_PTR,

    /**
     * Pointer to a {@code TAG_Byte_Array} payload in the retained buffer. The offset addresses the
     * 4-byte big-endian length prefix; the {@code length} payload bytes immediately follow.
     */
    BYTE_ARRAY_PTR,

    /**
     * Pointer to a {@code TAG_Int_Array} payload in the retained buffer. The offset addresses the
     * 4-byte big-endian length prefix; {@code length * 4} payload bytes (big-endian ints)
     * immediately follow.
     */
    INT_ARRAY_PTR,

    /**
     * Pointer to a {@code TAG_Long_Array} payload in the retained buffer. The offset addresses the
     * 4-byte big-endian length prefix; {@code length * 8} payload bytes (big-endian longs)
     * immediately follow.
     */
    LONG_ARRAY_PTR,

    /**
     * Pointer to a {@code TAG_String} payload in the retained buffer. The offset addresses the
     * 2-byte big-endian unsigned length prefix; {@code length} bytes of modified UTF-8 immediately
     * follow.
     */
    STRING_PTR,

    /**
     * Pointer to a compound entry's name in the retained buffer. The offset addresses the 2-byte
     * big-endian length prefix of the modified-UTF-8 key. Always emitted immediately before the
     * tape element(s) describing the matching value, so each compound entry contributes
     * {@code 1 + valueElementCount} tape slots.
     */
    KEY_PTR,

    /**
     * Opening marker for a {@code TAG_List}. The packed value carries
     * {@code (approxLen << 32) | endTapeOffset}, where {@code endTapeOffset} is the tape index of
     * the matching {@link #LIST_END} marker, allowing O(1) skip-past-subtree.
     *
     * <p>Encoded via {@link TapeElement#packListHeader(byte, int, int)}; unpack via
     * {@link TapeElement#unpackApproxLen(long)} and {@link TapeElement#unpackEndOffset(long)}. The
     * 8-bit slot above {@code approxLen} carries the wire element-id (see
     * {@link TapeElement#unpackListElementId(long)}) so empty lists round-trip with the same
     * elementId as the production parser preserves.</p>
     */
    LIST_HEADER,

    /**
     * Opening marker for a {@code TAG_Compound}. Same packing as {@link #LIST_HEADER}; the
     * matching close is {@link #COMPOUND_END}.
     */
    COMPOUND_HEADER,

    /**
     * Closing marker for a {@code TAG_List}. Carries the originating header's tape index in the
     * low 32 bits as a back-reference, useful for one-pass walks that need to know where the list
     * began without an explicit stack.
     */
    LIST_END,

    /**
     * Closing marker for a {@code TAG_Compound}. Same back-reference semantics as
     * {@link #LIST_END}.
     */
    COMPOUND_END

}
