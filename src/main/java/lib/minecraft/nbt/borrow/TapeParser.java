package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.exception.NbtException;
import lib.minecraft.nbt.exception.NbtMaxDepthException;
import lib.minecraft.nbt.io.NbtByteCodec;
import lib.minecraft.nbt.tags.TagType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Streaming binary parser that builds a {@link Tape} directly from an NBT {@code byte[]} without
 * materializing intermediate {@link lib.minecraft.nbt.tags.collection.CompoundTag CompoundTag}
 * instances.
 *
 * <p>Mirrors {@code simdnbt::borrow::compound::read_with_depth_check}
 * ({@code simdnbt/src/borrow/compound.rs:278-356}). The parser walks the input buffer once and
 * pushes packed tape entries describing the depth-first iteration order of the tree. Open
 * containers (compound and list) are tracked on a fixed-capacity 512-frame stack so deeply nested
 * adversarial input throws {@link NbtMaxDepthException} rather than {@link StackOverflowError}.</p>
 *
 * <p>Reads use {@link NbtByteCodec}'s {@link java.lang.invoke.VarHandle}-driven big-endian
 * primitives - no {@link java.io.DataInputStream} layer, no per-byte syscalls, no
 * {@code MemorySegment}. Pointer-kind tape elements record buffer offsets pointing at the wire
 * length prefix (2 bytes for strings/keys, 4 bytes for arrays); 8-byte primitives that do not fit
 * in the 56-bit inline payload also become {@code *_PTR} entries.</p>
 *
 * <p>The retained buffer in the produced {@link Tape} is the caller's input array, by reference -
 * callers must not mutate it. The C5 entry point will own the lifetime of the buffer through
 * {@code NbtFactory.borrowFromByteArray}.</p>
 */
@ApiStatus.Internal
public final class TapeParser {

    /**
     * Maximum nesting depth for open containers. Matches the existing 512 cap on every other
     * deserializer in the codebase (see {@code NbtInput.readContainerIterative}).
     */
    private static final int MAX_DEPTH = 512;

    /**
     * Frame-kind sentinel for compound frames: {@code openFrameRemaining[sp] == COMPOUND_FRAME}.
     * Compound frames terminate on a {@code TAG_End} byte rather than a known element count, so
     * the remaining-count field is unused.
     */
    private static final int COMPOUND_FRAME = -1;

    /**
     * The retained buffer being parsed. Pointer-kind tape elements record offsets into this array.
     */
    private final byte @NotNull [] input;

    /**
     * Current read position in {@link #input}.
     */
    private int position;

    /**
     * Growable packed-tape entry array. Doubled like {@code ArrayList} via
     * {@link #ensureCapacity(int)}.
     */
    private long @NotNull [] tape;

    /**
     * Number of populated entries in {@link #tape}.
     */
    private int tapeSize;

    /**
     * For each open frame, the tape index of the frame's {@code COMPOUND_HEADER} or
     * {@code LIST_HEADER} entry. Used to back-patch {@code endOffset} when the matching {@code _END}
     * is emitted.
     */
    private final int @NotNull [] openFrameTapeIndex;

    /**
     * For each open frame, the entry count accumulated so far ({@code COMPOUND}) or the number of
     * remaining elements to read ({@code LIST}). The discriminant is the value sign:
     * {@link #COMPOUND_FRAME} {@code (-1)} means compound, {@code >= 0} means list.
     */
    private final int @NotNull [] openFrameRemaining;

    /**
     * For each compound frame, the running entry count (incremented on each non-{@code TAG_End}
     * tag). Drives {@code approxLen} when the frame closes. Unused for list frames (the list's
     * advertised length doubles as its {@code approxLen}).
     */
    private final int @NotNull [] openFrameCompoundEntries;

    /**
     * For each list frame, the wire element-id (the byte that follows {@code TAG_List} on the
     * wire). Saved here so the matching {@code LIST_END} can re-pack the header with the
     * element-id intact, and so subsequent list elements are dispatched to the right reader without
     * re-reading the type byte. Unused for compound frames.
     */
    private final byte @NotNull [] openFrameListElementId;

    /**
     * Stack pointer; {@code -1} means empty. The topmost live frame is at {@code openFrame*[sp]}.
     */
    private int sp;

    private TapeParser(byte @NotNull [] input) {
        this.input = input;
        this.position = 0;
        // Two tape entries per buffer byte is a generous upper bound (worst case is all 1-byte
        // primitives in named compound entries; each contributes KEY_PTR + value = 2 entries).
        this.tape = new long[Math.max(16, input.length * 2)];
        this.tapeSize = 0;
        this.openFrameTapeIndex = new int[MAX_DEPTH];
        this.openFrameRemaining = new int[MAX_DEPTH];
        this.openFrameCompoundEntries = new int[MAX_DEPTH];
        this.openFrameListElementId = new byte[MAX_DEPTH];
        this.sp = -1;
    }

    /**
     * Parses the given binary NBT buffer into a tape.
     *
     * <p>The buffer is retained by the returned tape - pointer-kind tape elements address bytes
     * inside it. The caller must not mutate the array after passing it in.</p>
     *
     * @param input the binary NBT buffer; must start with a {@code TAG_Compound} root (id
     *     {@code 10}) followed by a 2-byte big-endian root-name length and the name bytes
     * @return a fully populated tape backed by {@code input}
     * @throws IOException on malformed input - bad type id, truncated buffer, deeper than
     *     {@value #MAX_DEPTH} levels of nesting (wrapped {@link NbtMaxDepthException}), or
     *     a tape size that overflows the 24-bit {@code endOffset} field
     */
    public static @NotNull Tape parse(byte @NotNull [] input) throws IOException {
        TapeParser parser = new TapeParser(input);
        parser.parseRoot();
        return new Tape(parser.tape, parser.tapeSize, input);
    }

    private void parseRoot() throws IOException {
        // Wire layout: [0]=TAG_Compound, [1..2]=root-name length, [3..]=compound body.
        if (this.input.length < 3)
            throw new NbtException("Buffer too short for an NBT root (need at least 3 bytes, got %d)", this.input.length);

        byte rootType = this.readByte();

        if (rootType != TagType.COMPOUND.getId())
            throw new NbtException("Root tag must be TAG_Compound, found id %d", rootType & 0xFF);

        // Root name (modified UTF-8): 2-byte length + bytes. Tape skips the root name (no semantic
        // value); pointer-addressing is unnecessary here since no consumer needs the root name.
        int rootNameLen = this.readUnsignedShort();
        this.advance(rootNameLen);

        // Push the root COMPOUND_HEADER + open the root compound frame, then drive the iterative
        // loop to drain.
        int rootHeaderIdx = this.pushPlaceholderCompoundHeader();
        this.openFrame(rootHeaderIdx, COMPOUND_FRAME, (byte) 0);

        this.driveStack();

        if (this.sp != -1)
            throw new NbtException("Parser ended with %d open frames remaining", this.sp + 1);
    }

    /**
     * Pumps the open-frame stack until empty. Each iteration consumes a single wire entry from the
     * top frame: a key + value pair for compound frames, a single value for list frames. Container
     * values open a new frame and yield to the next iteration; leaves emit their tape element(s)
     * and stay on the same frame. End conditions ({@code TAG_End} for compounds,
     * {@code remaining == 0} for lists) close the frame, emit the matching {@code _END} marker,
     * and back-patch the originating header's {@code endOffset}.
     */
    private void driveStack() throws IOException {
        while (this.sp >= 0) {
            int remaining = this.openFrameRemaining[this.sp];

            if (remaining == COMPOUND_FRAME) {
                this.stepCompound();
                continue;
            }

            this.stepList(remaining);
        }
    }

    private void stepCompound() throws IOException {
        byte typeId = this.readByte();

        if (typeId == TagType.END.getId()) {
            this.closeCompoundFrame();
            return;
        }

        // Key (modified UTF-8): tape stores the offset of the 2-byte length prefix; the parser
        // skips past length + bytes without decoding. Decoding happens lazily in materialize() or
        // in C3's borrowed navigators.
        int keyOffset = this.position;
        int keyLen = this.readUnsignedShort();
        this.advance(keyLen);

        this.appendTape(TapeElement.pack(TapeKind.KEY_PTR, keyOffset));
        this.openFrameCompoundEntries[this.sp]++;

        this.dispatchValue(typeId);
    }

    private void stepList(int remaining) throws IOException {
        if (remaining == 0) {
            this.closeListFrame();
            return;
        }

        this.openFrameRemaining[this.sp] = remaining - 1;
        // Element id was stashed when the LIST_HEADER was emitted - dispatching by it lets us read
        // the next element without re-consuming a type byte (lists do not repeat the element type).
        this.dispatchValue(this.openFrameListElementId[this.sp]);
    }

    /**
     * Reads and tape-emits a single value of the given type id. Mirrors the type dispatch in
     * {@code simdnbt::borrow::compound::read_tag} ({@code borrow/compound.rs:358-424}). Container
     * values ({@code TAG_List}, {@code TAG_Compound}) push a new frame and return without consuming
     * any further wire bytes - the next call to {@link #driveStack()} resumes inside the new frame.
     */
    private void dispatchValue(byte typeId) throws IOException {
        switch (typeId) {
            case 1 -> { // BYTE
                byte v = this.readByte();
                this.appendTape(TapeElement.pack(TapeKind.BYTE_INLINE, v));
            }
            case 2 -> { // SHORT
                short v = this.readShort();
                this.appendTape(TapeElement.pack(TapeKind.SHORT_INLINE, v));
            }
            case 3 -> { // INT
                int v = this.readInt();
                this.appendTape(TapeElement.pack(TapeKind.INT_INLINE, v));
            }
            case 4 -> { // LONG (does not fit in 56 bits - tape stores a buffer offset)
                int offset = this.position;
                this.advance(8);
                this.appendTape(TapeElement.pack(TapeKind.LONG_PTR, offset));
            }
            case 5 -> { // FLOAT (32-bit raw bits stored inline)
                int bits = this.readInt();
                this.appendTape(TapeElement.pack(TapeKind.FLOAT_INLINE, bits));
            }
            case 6 -> { // DOUBLE (64-bit - tape stores a buffer offset)
                int offset = this.position;
                this.advance(8);
                this.appendTape(TapeElement.pack(TapeKind.DOUBLE_PTR, offset));
            }
            case 7 -> { // BYTE_ARRAY - offset addresses the 4-byte length prefix
                int offset = this.position;
                int len = this.readInt();
                this.advance(len);
                this.appendTape(TapeElement.pack(TapeKind.BYTE_ARRAY_PTR, offset));
            }
            case 8 -> { // STRING - offset addresses the 2-byte length prefix
                int offset = this.position;
                int len = this.readUnsignedShort();
                this.advance(len);
                this.appendTape(TapeElement.pack(TapeKind.STRING_PTR, offset));
            }
            case 9 -> this.openListValue(); // LIST
            case 10 -> this.openCompoundValue(); // COMPOUND
            case 11 -> { // INT_ARRAY - offset addresses the 4-byte length prefix
                int offset = this.position;
                int len = this.readInt();
                this.advance(Math.multiplyExact(len, 4));
                this.appendTape(TapeElement.pack(TapeKind.INT_ARRAY_PTR, offset));
            }
            case 12 -> { // LONG_ARRAY - offset addresses the 4-byte length prefix
                int offset = this.position;
                int len = this.readInt();
                this.advance(Math.multiplyExact(len, 8));
                this.appendTape(TapeElement.pack(TapeKind.LONG_ARRAY_PTR, offset));
            }
            default -> throw new NbtException("Unknown tag id encountered while parsing buffer: %d", typeId & 0xFF);
        }
    }

    private void openCompoundValue() {
        int headerIdx = this.pushPlaceholderCompoundHeader();
        this.openFrame(headerIdx, COMPOUND_FRAME, (byte) 0);
    }

    private void openListValue() throws IOException {
        byte elementType = this.readByte();
        int rawLength = this.readInt();
        int length = Math.max(0, rawLength);

        int headerIdx = this.appendTape(TapeElement.packListHeader(elementType, length, 0));

        // Empty lists still get a frame so the close path emits LIST_END uniformly. The element-id
        // is stashed in the LIST_HEADER's reserved byte (see TapeElement.packListHeader) so empty
        // lists round-trip with their wire elementId preserved - matches NbtInput.readContainerIterative
        // and Tape.encode (C1) on the same input.
        this.openFrame(headerIdx, length, elementType);
    }

    private int pushPlaceholderCompoundHeader() {
        return this.appendTape(TapeElement.packCompoundHeader(0, 0));
    }

    private void openFrame(int headerTapeIdx, int remaining, byte elementId) {
        // Depth check mirrors the legacy `if (++depth >= 512) throw` semantic: the 512th nested
        // container is rejected. sp is post-increment, so sp >= MAX_DEPTH after the push means we
        // are about to occupy slot 512, which exceeds the cap.
        int newSp = this.sp + 1;

        if (newSp >= MAX_DEPTH)
            throw new NbtMaxDepthException();

        this.sp = newSp;
        this.openFrameTapeIndex[newSp] = headerTapeIdx;
        this.openFrameRemaining[newSp] = remaining;
        this.openFrameCompoundEntries[newSp] = 0;
        this.openFrameListElementId[newSp] = elementId;
    }

    private void closeCompoundFrame() {
        int headerIdx = this.openFrameTapeIndex[this.sp];
        int entries = this.openFrameCompoundEntries[this.sp];
        this.sp--;

        int endIdx = this.appendTape(TapeElement.pack(TapeKind.COMPOUND_END, headerIdx));
        this.checkEndOffsetFits(endIdx);
        this.tape[headerIdx] = TapeElement.packCompoundHeader(entries, endIdx);
    }

    private void closeListFrame() {
        int headerIdx = this.openFrameTapeIndex[this.sp];
        // Element-id was packed into the placeholder header by openListValue; preserve it on the
        // back-patch so an empty list still carries its wire element-id (matters for inputs like
        // simple_player.dat's empty Inventory list at type id 10).
        byte elementId = TapeElement.unpackListElementId(this.tape[headerIdx]);
        int approxLen = TapeElement.unpackApproxLen(this.tape[headerIdx]);
        this.sp--;

        int endIdx = this.appendTape(TapeElement.pack(TapeKind.LIST_END, headerIdx));
        this.checkEndOffsetFits(endIdx);
        this.tape[headerIdx] = TapeElement.packListHeader(elementId, approxLen, endIdx);
    }

    private void checkEndOffsetFits(int endIdx) {
        // packCompoundHeader / packListHeader also check this, but throwing earlier with a parser-
        // specific message gives the user a clearer error than IllegalArgumentException from the
        // packer.
        if (endIdx > TapeElement.MAX_END_OFFSET)
            throw new NbtException(
                "Tape size %d exceeds 24-bit endOffset cap (%d) - input is too large to address",
                endIdx, TapeElement.MAX_END_OFFSET
            );
    }

    private int appendTape(long element) {
        this.ensureCapacity(this.tapeSize + 1);
        int idx = this.tapeSize;
        this.tape[idx] = element;
        this.tapeSize = idx + 1;
        return idx;
    }

    private void ensureCapacity(int needed) {
        if (needed <= this.tape.length)
            return;

        int newSize = Math.max(this.tape.length * 2, needed);
        long[] grown = new long[newSize];
        System.arraycopy(this.tape, 0, grown, 0, this.tapeSize);
        this.tape = grown;
    }

    // ------------------------------------------------------------------
    // Buffer reads (bounds-checked, advancing position)
    // ------------------------------------------------------------------

    private byte readByte() throws IOException {
        this.requireRemaining(1);
        return this.input[this.position++];
    }

    private int readUnsignedShort() throws IOException {
        this.requireRemaining(2);
        int v = NbtByteCodec.getUnsignedShort(this.input, this.position);
        this.position += 2;
        return v;
    }

    private short readShort() throws IOException {
        this.requireRemaining(2);
        short v = NbtByteCodec.getShort(this.input, this.position);
        this.position += 2;
        return v;
    }

    private int readInt() throws IOException {
        this.requireRemaining(4);
        int v = NbtByteCodec.getInt(this.input, this.position);
        this.position += 4;
        return v;
    }

    private void advance(int byteCount) throws IOException {
        if (byteCount < 0)
            throw new NbtException("Negative advance %d at offset %d", byteCount, this.position);

        this.requireRemaining(byteCount);
        this.position += byteCount;
    }

    private void requireRemaining(int byteCount) throws IOException {
        // Manual bounds-check so a truncated buffer surfaces a helpful NbtException rather than
        // an ArrayIndexOutOfBoundsException from the underlying array access.
        if (this.position + byteCount > this.input.length)
            throw new NbtException(
                "Truncated NBT input - need %d bytes at offset %d, only %d available",
                byteCount, this.position, this.input.length - this.position
            );
    }

}
