package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.NbtFactory;
import lib.minecraft.nbt.exception.NbtException;
import lib.minecraft.nbt.io.NbtByteCodec;
import lib.minecraft.nbt.io.NbtKnownKeys;
import lib.minecraft.nbt.io.NbtModifiedUtf8;
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

import java.io.IOException;
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
 *       elements address. {@link #encode(CompoundTag)} retains the
 *       {@link NbtFactory#toByteArray(CompoundTag)} output (including its {@code 0x0A 0x00 0x00}
 *       root preamble); {@link TapeParser#parse(byte[])} retains the caller's input array
 *       directly.</li>
 * </ul>
 *
 * <p>All container traversal helpers run in {@code O(n)} where {@code n} is the number of tape
 * entries, never the buffer length, matching simdnbt's design (see
 * {@code simdnbt::borrow::tape::TapeElement::skip_offset}).</p>
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
     * must not mutate it. {@link TapeParser#parse(byte[])} retains the caller's input by
     * reference; {@link #encode(CompoundTag)} retains its own serializer output.
     */
    final byte @NotNull [] buffer;

    /**
     * Package-private all-arg constructor used by {@link #encode(CompoundTag)} and by
     * {@link TapeParser#parse(byte[])}. Public construction goes through one of those entry
     * points - the tape's invariants (kind ordering, matched headers, valid offsets) are not
     * re-validated here.
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
    // Encoder (round-trips a CompoundTag through NbtFactory.toByteArray + TapeParser.parse)
    // ------------------------------------------------------------------

    /**
     * Encodes a fully-materialized {@link CompoundTag} into a tape.
     *
     * <p>Serializes {@code root} via {@link NbtFactory#toByteArray(CompoundTag)} (no compression),
     * then hands the produced bytes to {@link TapeParser#parse(byte[])}. The retained buffer is
     * the full serializer output including its {@code 0x0A 0x00 0x00} root preamble; pointer-kind
     * tape elements address bytes inside that payload.</p>
     *
     * <p>Used by the round-trip tests as a convenience; production callers building a tape from
     * raw NBT bytes should call {@link TapeParser#parse(byte[])} directly to avoid the extra
     * serialize step.</p>
     *
     * @param root the compound to encode
     * @return a fully populated tape
     * @throws NbtException if {@code NbtFactory.toByteArray} fails or the produced bytes are
     *     malformed (which would indicate an internal serializer/parser disagreement)
     */
    public static @NotNull Tape encode(@NotNull CompoundTag root) {
        try {
            byte[] buffer = NbtFactory.toByteArray(root);
            return TapeParser.parse(buffer);
        } catch (IOException exception) {
            throw new NbtException(exception, "Failed to encode CompoundTag into a tape");
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
            String key = decodeUtf8Known(ctx.buffer, keyOffset);
            ctx.tapeIndex++;

            compound.put(key, readValue(ctx));
        }
    }

    private static @NotNull lib.minecraft.nbt.tags.Tag<?> readValue(@NotNull MaterializeContext ctx) {
        long element = ctx.elements[ctx.tapeIndex];
        TapeKind kind = TapeElement.unpackKind(element);

        return switch (kind) {
            case BYTE_INLINE -> {
                ctx.tapeIndex++;
                yield ByteTag.of((byte) TapeElement.unpackValue(element));
            }
            case SHORT_INLINE -> {
                ctx.tapeIndex++;
                yield ShortTag.of((short) TapeElement.unpackValue(element));
            }
            case INT_INLINE -> {
                ctx.tapeIndex++;
                yield IntTag.of((int) TapeElement.unpackValue(element));
            }
            case FLOAT_INLINE -> {
                ctx.tapeIndex++;
                yield new FloatTag(Float.intBitsToFloat((int) TapeElement.unpackValue(element)));
            }
            case LONG_PTR -> {
                int offset = (int) TapeElement.unpackValue(element);
                ctx.tapeIndex++;
                yield LongTag.of(NbtByteCodec.getLong(ctx.buffer, offset));
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
    // Borrow-mode entry point (Phase C3)
    // ------------------------------------------------------------------

    /**
     * Returns a {@link BorrowedCompoundTag} navigator over the root compound at tape index 0.
     *
     * <p>This is the user-facing entry point into the borrow API in C3. C5 will wire
     * {@code NbtFactory.borrowFromByteArray} to {@code TapeParser.parse(bytes).root()} as a
     * one-call entry point that does not depend on internal types.</p>
     *
     * @return a navigator over the root compound
     * @throws NbtException if the tape is empty or its root is not a {@code COMPOUND_HEADER}
     */
    public @NotNull BorrowedCompoundTag root() {
        if (this.size < 1)
            throw new NbtException("Empty tape has no root");

        long header = this.elements[0];

        if (TapeElement.unpackKind(header) != TapeKind.COMPOUND_HEADER)
            throw new NbtException(
                "Tape root is not a COMPOUND_HEADER: %s", TapeElement.unpackKind(header));

        return new BorrowedCompoundTag(this, 0);
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
     * {@link #materialize()}, and by {@link BorrowedCompoundTag#get(String)} as the user-facing
     * lookup path.</p>
     *
     * <p>Compares each entry's key bytes against {@code key} via
     * {@link MutfStringView#equalsString(String)} - which takes a byte-level fast path when the
     * key is ASCII and avoids materializing a {@link String} per entry during the linear scan.
     * Most NBT keys ({@code "id"}, {@code "Count"}, {@code "tag"}, {@code "display"},
     * {@code "ExtraAttributes"}) are ASCII, so the typical lookup pays zero allocations for the
     * scan itself.</p>
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
            MutfStringView view = MutfStringView.fromTagOffset(this.buffer, keyOffset);
            int valueIdx = idx + 1;

            if (view.equalsString(key))
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

    /**
     * Decodes a length-prefixed modified-UTF-8 key at {@code offset}, returning the canonical
     * {@link NbtKnownKeys} interned string on a hit and falling through to {@link #decodeUtf8} on
     * a miss.
     *
     * <p>Mirror of {@link BorrowedTagSupport#decodeUtf8KnownAt(byte[], int)} - kept private to
     * preserve the package-private surface of {@code Tape}'s materializer. Same length-bucket
     * probe; same allocation-free outcome on hits to {@code id}, {@code Count}, {@code tag} and
     * the rest of the vanilla and SkyBlock vocabulary.</p>
     */
    private static @NotNull String decodeUtf8Known(byte @NotNull [] buffer, int offset) {
        int len = NbtByteCodec.getUnsignedShort(buffer, offset);
        String known = NbtKnownKeys.match(buffer, offset + 2, len);

        if (known != null)
            return known;

        return decodeUtf8(buffer, offset);
    }

}
