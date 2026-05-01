package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.NbtFactory;
import lib.minecraft.nbt.exception.NbtException;
import lib.minecraft.nbt.io.NbtByteCodec;
import lib.minecraft.nbt.io.NbtModifiedUtf8;
import lib.minecraft.nbt.tags.TagType;
import lib.minecraft.nbt.tags.array.ByteArrayTag;
import lib.minecraft.nbt.tags.array.IntArrayTag;
import lib.minecraft.nbt.tags.array.LongArrayTag;
import lib.minecraft.nbt.tags.collection.CompoundTag;
import lib.minecraft.nbt.tags.collection.ListTag;
import lib.minecraft.nbt.tags.primitive.ByteTag;
import lib.minecraft.nbt.tags.primitive.DoubleTag;
import lib.minecraft.nbt.tags.primitive.FloatTag;
import lib.minecraft.nbt.tags.primitive.IntTag;
import lib.minecraft.nbt.tags.primitive.LongTag;
import lib.minecraft.nbt.tags.primitive.ShortTag;
import lib.minecraft.nbt.tags.primitive.StringTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.UTFDataFormatException;

/**
 * Flat tape representation of an NBT compound tree.
 *
 * <p>A tape is a triple {@code (long[] elements, int size, byte[] buffer)}:</p>
 * <ul>
 *   <li>{@code elements} is a packed tape of {@link TapeKind}-tagged {@code long}s. The first
 *       valid element is a {@link TapeKind#COMPOUND_HEADER} for the root and the last is the
 *       matching {@link TapeKind#COMPOUND_END}; everything between describes the tree in
 *       depth-first iteration order.</li>
 *   <li>{@code size} is the number of populated entries in {@code elements} (the array may be
 *       over-allocated).</li>
 *   <li>{@code buffer} is a {@code byte[]} carrying the binary NBT bytes that pointer-kind tape
 *       elements address. C1 retains the entire serialized compound including its
 *       {@code 0x0A 0x00 0x00} root preamble; C2's streaming parser will retain whatever the
 *       caller passed in.</li>
 * </ul>
 *
 * <p>All container traversal helpers run in {@code O(n)} where {@code n} is the number of tape
 * entries, never the buffer length, matching simdnbt's design (see
 * {@code simdnbt::borrow::tape::TapeElement::skip_offset}).</p>
 *
 * <p><b>C1 limitation</b> - {@link #encode(CompoundTag)} round-trips a fully-materialized
 * {@link CompoundTag} through {@link NbtFactory#toByteArray(CompoundTag)} just so we have bytes
 * to walk. This is testability scaffolding; C2 introduces a streaming binary parser that walks a
 * caller-supplied {@code byte[]} once without ever materializing a {@link CompoundTag}.
 * {@link #materialize()} is the inverse used by the C1 round-trip tests and by the parity test
 * that lands alongside the C5 entry point.</p>
 */
@ApiStatus.Experimental
public final class Tape {

    /**
     * Sentinel returned by {@link #findChildTapeIndex(int, String)} when no entry matches.
     */
    public static final int NOT_FOUND = -1;

    /**
     * Packed tape entries. May be over-allocated; only the first {@link #size} entries are valid.
     */
    final long @NotNull [] elements;

    /**
     * Number of valid entries in {@link #elements}.
     */
    final int size;

    /**
     * Retained byte buffer that pointer-kind tape elements address. Owned by the tape; callers
     * must not mutate it. C2 will allow callers to pass their own buffer in.
     */
    final byte @NotNull [] buffer;

    /**
     * Package-private all-arg constructor used by {@link #encode(CompoundTag)} and (in C2) by the
     * streaming parser. Public construction goes through {@link #encode(CompoundTag)} only - the
     * tape's invariants (kind ordering, matched headers, valid offsets) are not re-validated
     * here.
     *
     * @param elements packed tape entries
     * @param size number of valid entries in {@code elements}
     * @param buffer retained byte buffer pointer-kind elements address
     */
    Tape(long @NotNull [] elements, int size, byte @NotNull [] buffer) {
        this.elements = elements;
        this.size = size;
        this.buffer = buffer;
    }

    /**
     * Number of populated tape entries.
     *
     * <p>Includes the bracketing root {@code COMPOUND_HEADER} / {@code COMPOUND_END} and the
     * {@code KEY_PTR} elements that precede every compound entry.</p>
     *
     * @return populated entry count
     */
    public int tapeSize() {
        return this.size;
    }

    /**
     * Returns a packed tape element at the given index.
     *
     * @param index tape index, {@code 0..tapeSize()-1}
     * @return the packed {@code long} entry
     * @throws IndexOutOfBoundsException if {@code index} is outside the valid range
     */
    public long elementAt(int index) {
        if (index < 0 || index >= this.size)
            throw new IndexOutOfBoundsException("tape index out of range: " + index);

        return this.elements[index];
    }

    /**
     * Returns the retained byte buffer. Callers must not mutate the returned array.
     *
     * @return the retained byte buffer
     */
    public byte @NotNull [] buffer() {
        return this.buffer;
    }

    // ------------------------------------------------------------------
    // Encoder (C1 - testability scaffold; C2 replaces with streaming parser)
    // ------------------------------------------------------------------

    /**
     * Encodes a fully-materialized {@link CompoundTag} into a tape.
     *
     * <p>Serializes {@code root} via {@link NbtFactory#toByteArray(CompoundTag)} (no compression),
     * then walks the resulting bytes once, pushing tape entries that mirror the binary structure.
     * The retained buffer is the full {@code NbtFactory.toByteArray} output including its
     * {@code 0x0A 0x00 0x00} root preamble; pointer-kind tape elements address bytes inside that
     * payload.</p>
     *
     * <p><b>C1 only.</b> C2 replaces the body of this factory with a thin wrapper that delegates
     * to a {@code TapeParser.parse(byte[])} that walks the bytes directly without going through
     * {@link CompoundTag} first.</p>
     *
     * @param root the compound to encode
     * @return a fully populated tape
     * @throws NbtException if {@code NbtFactory.toByteArray} fails or the produced bytes are
     *     malformed (which would indicate an internal serializer/parser disagreement)
     */
    public static @NotNull Tape encode(@NotNull CompoundTag root) {
        byte[] buffer = NbtFactory.toByteArray(root);
        return walkBuffer(buffer);
    }

    private static @NotNull Tape walkBuffer(byte @NotNull [] buffer) {
        // Buffer layout: [0]=COMPOUND_ID, [1..2]=root name length (always 0), [3..]=compound body
        // terminated by TAG_End. The tape skips the root name (it has no semantic value) but
        // emits a COMPOUND_HEADER for the root so consumers see a well-framed tree.
        if (buffer.length < 3 || buffer[0] != TagType.COMPOUND.getId())
            throw new NbtException("Tape encoder expected a TAG_Compound root in the serialized buffer");

        // Pre-size the tape generously: each tag pushes at most 2 elements (KEY_PTR + value, or
        // header + content + end). Doubling buffer length is a safe upper bound for a worst-case
        // payload of all 1-byte primitives in named entries.
        long[] elements = new long[Math.max(16, buffer.length * 2)];
        int[] state = new int[]{0, 3}; // [tapeSize, bufferPos]

        // Root compound header - back-patched once we know the matching END's tape index.
        int rootHeaderIdx = state[0];
        elements = ensure(elements, state[0] + 1);
        elements[state[0]++] = TapeElement.packCompoundHeader(0, 0); // placeholder

        WalkContext ctx = new WalkContext(buffer, elements, state);
        int approxLen = walkCompoundBody(ctx);
        elements = ctx.elements;

        // Emit the root END and back-patch the header.
        elements = ensure(elements, ctx.state[0] + 1);
        int rootEndIdx = ctx.state[0];
        elements[ctx.state[0]++] = TapeElement.pack(TapeKind.COMPOUND_END, rootHeaderIdx);
        elements[rootHeaderIdx] = TapeElement.packCompoundHeader(approxLen, rootEndIdx);

        return new Tape(elements, ctx.state[0], buffer);
    }

    /**
     * Walks the body of a compound starting at {@code state[1]}, emitting {@code KEY_PTR + value}
     * tape pairs until a TAG_End byte. Returns the number of entries (the {@code approxLen} for
     * the matching header).
     */
    private static int walkCompoundBody(@NotNull WalkContext ctx) {
        int entries = 0;

        while (true) {
            byte typeId = ctx.buffer[ctx.state[1]++];

            if (typeId == TagType.END.getId())
                return entries;

            // Key (modified UTF-8): 2-byte length prefix then bytes. Tape stores the offset of
            // the length prefix.
            int keyOffset = ctx.state[1];
            int keyLen = NbtByteCodec.getUnsignedShort(ctx.buffer, ctx.state[1]);
            ctx.state[1] += 2 + keyLen;

            ctx.elements = ensure(ctx.elements, ctx.state[0] + 1);
            ctx.elements[ctx.state[0]++] = TapeElement.pack(TapeKind.KEY_PTR, keyOffset);

            walkValue(ctx, typeId);
            entries++;
        }
    }

    /**
     * Walks a single tag value of {@code typeId} starting at {@code state[1]}, emitting one or
     * more tape elements.
     */
    private static void walkValue(@NotNull WalkContext ctx, byte typeId) {
        switch (typeId) {
            case 1 -> { // BYTE
                byte v = ctx.buffer[ctx.state[1]++];
                ctx.elements = ensure(ctx.elements, ctx.state[0] + 1);
                ctx.elements[ctx.state[0]++] = TapeElement.pack(TapeKind.BYTE_INLINE, v);
            }
            case 2 -> { // SHORT
                short v = NbtByteCodec.getShort(ctx.buffer, ctx.state[1]);
                ctx.state[1] += 2;
                ctx.elements = ensure(ctx.elements, ctx.state[0] + 1);
                ctx.elements[ctx.state[0]++] = TapeElement.pack(TapeKind.SHORT_INLINE, v);
            }
            case 3 -> { // INT
                int v = NbtByteCodec.getInt(ctx.buffer, ctx.state[1]);
                ctx.state[1] += 4;
                ctx.elements = ensure(ctx.elements, ctx.state[0] + 1);
                ctx.elements[ctx.state[0]++] = TapeElement.pack(TapeKind.INT_INLINE, v);
            }
            case 4 -> { // LONG
                int offset = ctx.state[1];
                ctx.state[1] += 8;
                ctx.elements = ensure(ctx.elements, ctx.state[0] + 1);
                ctx.elements[ctx.state[0]++] = TapeElement.pack(TapeKind.LONG_PTR, offset);
            }
            case 5 -> { // FLOAT
                int bits = NbtByteCodec.getInt(ctx.buffer, ctx.state[1]);
                ctx.state[1] += 4;
                ctx.elements = ensure(ctx.elements, ctx.state[0] + 1);
                ctx.elements[ctx.state[0]++] = TapeElement.pack(TapeKind.FLOAT_INLINE, bits);
            }
            case 6 -> { // DOUBLE
                int offset = ctx.state[1];
                ctx.state[1] += 8;
                ctx.elements = ensure(ctx.elements, ctx.state[0] + 1);
                ctx.elements[ctx.state[0]++] = TapeElement.pack(TapeKind.DOUBLE_PTR, offset);
            }
            case 7 -> { // BYTE_ARRAY
                int offset = ctx.state[1];
                int len = NbtByteCodec.getInt(ctx.buffer, ctx.state[1]);
                ctx.state[1] += 4 + len;
                ctx.elements = ensure(ctx.elements, ctx.state[0] + 1);
                ctx.elements[ctx.state[0]++] = TapeElement.pack(TapeKind.BYTE_ARRAY_PTR, offset);
            }
            case 8 -> { // STRING
                int offset = ctx.state[1];
                int len = NbtByteCodec.getUnsignedShort(ctx.buffer, ctx.state[1]);
                ctx.state[1] += 2 + len;
                ctx.elements = ensure(ctx.elements, ctx.state[0] + 1);
                ctx.elements[ctx.state[0]++] = TapeElement.pack(TapeKind.STRING_PTR, offset);
            }
            case 9 -> walkList(ctx); // LIST
            case 10 -> walkCompound(ctx); // COMPOUND
            case 11 -> { // INT_ARRAY
                int offset = ctx.state[1];
                int len = NbtByteCodec.getInt(ctx.buffer, ctx.state[1]);
                ctx.state[1] += 4 + (len << 2);
                ctx.elements = ensure(ctx.elements, ctx.state[0] + 1);
                ctx.elements[ctx.state[0]++] = TapeElement.pack(TapeKind.INT_ARRAY_PTR, offset);
            }
            case 12 -> { // LONG_ARRAY
                int offset = ctx.state[1];
                int len = NbtByteCodec.getInt(ctx.buffer, ctx.state[1]);
                ctx.state[1] += 4 + (len << 3);
                ctx.elements = ensure(ctx.elements, ctx.state[0] + 1);
                ctx.elements[ctx.state[0]++] = TapeElement.pack(TapeKind.LONG_ARRAY_PTR, offset);
            }
            default -> throw new NbtException("Unknown tag id encountered while walking buffer: " + typeId);
        }
    }

    private static void walkCompound(@NotNull WalkContext ctx) {
        int headerIdx = ctx.state[0];
        ctx.elements = ensure(ctx.elements, headerIdx + 1);
        ctx.elements[ctx.state[0]++] = TapeElement.packCompoundHeader(0, 0); // placeholder

        int entries = walkCompoundBody(ctx);

        ctx.elements = ensure(ctx.elements, ctx.state[0] + 1);
        int endIdx = ctx.state[0];
        ctx.elements[ctx.state[0]++] = TapeElement.pack(TapeKind.COMPOUND_END, headerIdx);
        ctx.elements[headerIdx] = TapeElement.packCompoundHeader(entries, endIdx);
    }

    private static void walkList(@NotNull WalkContext ctx) {
        byte elementType = ctx.buffer[ctx.state[1]++];
        int length = NbtByteCodec.getInt(ctx.buffer, ctx.state[1]);
        ctx.state[1] += 4;

        int headerIdx = ctx.state[0];
        ctx.elements = ensure(ctx.elements, headerIdx + 1);
        ctx.elements[ctx.state[0]++] = TapeElement.packListHeader(elementType, 0, 0); // placeholder

        // Empty lists may still carry a non-zero wire element type (e.g. an Inventory list that
        // happens to be empty). The element type is stashed in the header's 8-bit reserved slot
        // so materialize() can reconstruct an empty ListTag with the same elementId, which the
        // production parser also preserves (see NbtInput.readContainerIterative).
        if (length > 0) {
            for (int i = 0; i < length; i++)
                walkValue(ctx, elementType);
        }

        ctx.elements = ensure(ctx.elements, ctx.state[0] + 1);
        int endIdx = ctx.state[0];
        ctx.elements[ctx.state[0]++] = TapeElement.pack(TapeKind.LIST_END, headerIdx);
        ctx.elements[headerIdx] = TapeElement.packListHeader(elementType, length, endIdx);
    }

    private static long @NotNull [] ensure(long @NotNull [] elements, int needed) {
        if (needed <= elements.length)
            return elements;

        int newSize = Math.max(elements.length * 2, needed);
        long[] grown = new long[newSize];
        System.arraycopy(elements, 0, grown, 0, elements.length);
        return grown;
    }

    /**
     * Mutable state carrier passed through the recursive walk - avoids boxing two ints into an
     * {@code int[]} return on every recursive call. {@code state[0]} is the next free tape slot;
     * {@code state[1]} is the current buffer position.
     */
    private static final class WalkContext {

        final byte @NotNull [] buffer;
        long @NotNull [] elements;
        final int @NotNull [] state;

        WalkContext(byte @NotNull [] buffer, long @NotNull [] elements, int @NotNull [] state) {
            this.buffer = buffer;
            this.elements = elements;
            this.state = state;
        }

    }

    // ------------------------------------------------------------------
    // Materialization (inverse of encode; primary tool for round-trip tests)
    // ------------------------------------------------------------------

    /**
     * Walks the tape and reconstructs a fully-materialized {@link CompoundTag} by reading values
     * from the retained buffer at each tape entry's offset.
     *
     * <p>This is the inverse of {@link #encode(CompoundTag)}; the two together must round-trip
     * for every input. Used by the C1 {@code TapeRoundTripTest}, by the C2 streaming-parser
     * tests, and by the C5 borrow-vs-materializing parity test.</p>
     *
     * @return a freshly-allocated compound equivalent to the one originally encoded
     * @throws NbtException if the tape is structurally malformed (kind ordering or end-offset
     *     consistency)
     */
    public @NotNull CompoundTag materialize() {
        if (this.size < 2)
            throw new NbtException("Tape too short to materialize: size=%d", this.size);

        long header = this.elements[0];

        if (TapeElement.unpackKind(header) != TapeKind.COMPOUND_HEADER)
            throw new NbtException("Tape root is not a COMPOUND_HEADER: %s", TapeElement.unpackKind(header));

        MaterializeContext ctx = new MaterializeContext(this.elements, this.buffer);
        ctx.tapeIndex = 1;
        return readCompound(ctx);
    }

    private static @NotNull CompoundTag readCompound(@NotNull MaterializeContext ctx) {
        CompoundTag compound = new CompoundTag();

        while (true) {
            long element = ctx.elements[ctx.tapeIndex];
            TapeKind kind = TapeElement.unpackKind(element);

            if (kind == TapeKind.COMPOUND_END) {
                ctx.tapeIndex++;
                return compound;
            }

            if (kind != TapeKind.KEY_PTR)
                throw new NbtException("Expected KEY_PTR or COMPOUND_END inside compound, found %s", kind);

            int keyOffset = (int) TapeElement.unpackValue(element);
            String key = decodeUtf8(ctx.buffer, keyOffset);
            ctx.tapeIndex++;

            compound.put(key, readValue(ctx));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static @NotNull lib.minecraft.nbt.tags.Tag<?> readValue(@NotNull MaterializeContext ctx) {
        long element = ctx.elements[ctx.tapeIndex];
        TapeKind kind = TapeElement.unpackKind(element);

        return switch (kind) {
            case BYTE_INLINE -> {
                ctx.tapeIndex++;
                yield new ByteTag((byte) TapeElement.unpackValue(element));
            }
            case SHORT_INLINE -> {
                ctx.tapeIndex++;
                yield new ShortTag((short) TapeElement.unpackValue(element));
            }
            case INT_INLINE -> {
                ctx.tapeIndex++;
                yield new IntTag((int) TapeElement.unpackValue(element));
            }
            case FLOAT_INLINE -> {
                ctx.tapeIndex++;
                yield new FloatTag(Float.intBitsToFloat((int) TapeElement.unpackValue(element)));
            }
            case LONG_PTR -> {
                int offset = (int) TapeElement.unpackValue(element);
                ctx.tapeIndex++;
                yield new LongTag(NbtByteCodec.getLong(ctx.buffer, offset));
            }
            case DOUBLE_PTR -> {
                int offset = (int) TapeElement.unpackValue(element);
                ctx.tapeIndex++;
                yield new DoubleTag(NbtByteCodec.getDouble(ctx.buffer, offset));
            }
            case BYTE_ARRAY_PTR -> {
                int offset = (int) TapeElement.unpackValue(element);
                int len = NbtByteCodec.getInt(ctx.buffer, offset);
                byte[] data = new byte[len];
                System.arraycopy(ctx.buffer, offset + 4, data, 0, len);
                ctx.tapeIndex++;
                yield new ByteArrayTag(data);
            }
            case INT_ARRAY_PTR -> {
                int offset = (int) TapeElement.unpackValue(element);
                int len = NbtByteCodec.getInt(ctx.buffer, offset);
                int[] data = new int[len];
                NbtByteCodec.getIntArrayBE(ctx.buffer, offset + 4, data, 0, len);
                ctx.tapeIndex++;
                yield new IntArrayTag(data);
            }
            case LONG_ARRAY_PTR -> {
                int offset = (int) TapeElement.unpackValue(element);
                int len = NbtByteCodec.getInt(ctx.buffer, offset);
                long[] data = new long[len];
                NbtByteCodec.getLongArrayBE(ctx.buffer, offset + 4, data, 0, len);
                ctx.tapeIndex++;
                yield new LongArrayTag(data);
            }
            case STRING_PTR -> {
                int offset = (int) TapeElement.unpackValue(element);
                String value = decodeUtf8(ctx.buffer, offset);
                ctx.tapeIndex++;
                yield new StringTag(value);
            }
            case COMPOUND_HEADER -> {
                ctx.tapeIndex++;
                yield readCompound(ctx);
            }
            case LIST_HEADER -> {
                byte listElementId = TapeElement.unpackListElementId(element);
                int approxLen = TapeElement.unpackApproxLen(element);
                ctx.tapeIndex++;
                yield readList(ctx, listElementId, approxLen);
            }
            default -> throw new NbtException("Unexpected kind in value position: %s", kind);
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static @NotNull ListTag readList(@NotNull MaterializeContext ctx, byte elementId, int approxLen) {
        // ListTag(elementId, capacity) pre-seeds the element-type so an empty list still carries
        // the wire-derived elementId after materialize - matches NbtInput.readContainerIterative,
        // which uses the same constructor on the same elementId byte.
        ListTag list = new ListTag(elementId, approxLen);

        while (true) {
            long element = ctx.elements[ctx.tapeIndex];
            TapeKind kind = TapeElement.unpackKind(element);

            if (kind == TapeKind.LIST_END) {
                ctx.tapeIndex++;
                return list;
            }

            list.add(readValue(ctx));
        }
    }

    private static final class MaterializeContext {

        final long @NotNull [] elements;
        final byte @NotNull [] buffer;
        int tapeIndex;

        MaterializeContext(long @NotNull [] elements, byte @NotNull [] buffer) {
            this.elements = elements;
            this.buffer = buffer;
        }

    }

    // ------------------------------------------------------------------
    // Helper for shape tests + future C3 navigators
    // ------------------------------------------------------------------

    /**
     * Walks a compound starting at {@code compoundHeaderIndex} and returns the tape index of the
     * matching value entry whose preceding {@code KEY_PTR} resolves to {@code key}, or
     * {@link #NOT_FOUND} when no entry matches.
     *
     * <p>Used by {@code TapeShapeTest} to assert structural invariants without depending on
     * {@link #materialize()}. C3's {@code BorrowedCompoundTag} navigator will use the same
     * traversal pattern.</p>
     *
     * @param compoundHeaderIndex tape index of a {@link TapeKind#COMPOUND_HEADER}
     * @param key the modified-UTF-8 key to match against each entry's name
     * @return the tape index of the matching value element, or {@link #NOT_FOUND}
     * @throws NbtException if {@code compoundHeaderIndex} does not point at a compound header
     */
    public int findChildTapeIndex(int compoundHeaderIndex, @NotNull String key) {
        long header = this.elements[compoundHeaderIndex];

        if (TapeElement.unpackKind(header) != TapeKind.COMPOUND_HEADER)
            throw new NbtException("Index %d is not a COMPOUND_HEADER", compoundHeaderIndex);

        int endIdx = TapeElement.unpackEndOffset(header);
        int idx = compoundHeaderIndex + 1;

        while (idx < endIdx) {
            long element = this.elements[idx];
            TapeKind kind = TapeElement.unpackKind(element);

            if (kind != TapeKind.KEY_PTR)
                throw new NbtException("Expected KEY_PTR inside compound at tape index %d, found %s", idx, kind);

            int keyOffset = (int) TapeElement.unpackValue(element);
            String name = decodeUtf8(this.buffer, keyOffset);
            int valueIdx = idx + 1;

            if (name.equals(key))
                return valueIdx;

            idx = nextSibling(valueIdx);
        }

        return NOT_FOUND;
    }

    /**
     * Returns the tape index of the entry immediately following the value at {@code valueIndex},
     * skipping the entire subtree of any container value in O(1) via the header's {@code endOffset}
     * pointer.
     *
     * @param valueIndex tape index of a value element (not a {@code KEY_PTR})
     * @return the tape index of the next sibling
     */
    public int nextSibling(int valueIndex) {
        long element = this.elements[valueIndex];
        TapeKind kind = TapeElement.unpackKind(element);

        return switch (kind) {
            case COMPOUND_HEADER, LIST_HEADER -> TapeElement.unpackEndOffset(element) + 1;
            default -> valueIndex + 1;
        };
    }

    /**
     * Decodes a length-prefixed modified-UTF-8 string at {@code offset} in {@code buffer}.
     *
     * <p>{@link NbtModifiedUtf8#decode(byte[], int, int) NbtModifiedUtf8.decode} declares
     * {@link UTFDataFormatException} but the bytes already round-tripped through
     * {@link NbtFactory#toByteArray} (or, in C2+, through a parser that ran the same MUTF-8 probe
     * on the way in), so a malformed sequence here would indicate corruption of the retained
     * buffer rather than user input. Wrap as {@link NbtException} to keep the borrow API's
     * checked-exception surface free.</p>
     */
    private static @NotNull String decodeUtf8(byte @NotNull [] buffer, int offset) {
        int len = NbtByteCodec.getUnsignedShort(buffer, offset);
        try {
            return NbtModifiedUtf8.decode(buffer, offset + 2, len);
        } catch (UTFDataFormatException exception) {
            throw new NbtException(exception, "Malformed modified UTF-8 in tape buffer at offset %d", offset);
        }
    }

}
