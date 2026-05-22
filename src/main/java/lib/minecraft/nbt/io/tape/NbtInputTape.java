package lib.minecraft.nbt.io.tape;

import lib.minecraft.nbt.NbtFactory;
import lib.minecraft.nbt.exception.NbtFormatException;
import lib.minecraft.nbt.exception.NbtMaxDepthException;
import lib.minecraft.nbt.io.NbtInput;
import lib.minecraft.nbt.io.util.NbtByteCodec;
import lib.minecraft.nbt.io.util.NbtModifiedUtf8;
import lib.minecraft.nbt.tags.CompoundTag;
import lib.minecraft.nbt.tags.TagType;
import lib.minecraft.nbt.tags.borrow.Tape;
import lib.minecraft.nbt.tags.borrow.TapeElement;
import lib.minecraft.nbt.tags.borrow.TapeKind;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Tape-building parser for binary NBT. {@link #parse(byte[])} consumes a {@code byte[]} once and
 * returns a {@link Tape} - no intermediate {@link CompoundTag} instances are materialized.
 *
 * <p>Implements {@link NbtInput} for the primitive byte-reading surface only ({@code readByte},
 * {@code readShort}, ..., {@code readUTF}, the typed array reads). The structural
 * {@code readCompoundTag} / {@code readListTag} defaults are intentionally not specialized here -
 * the tape builder has its own non-recursive dispatcher that walks the wire format directly into
 * packed tape entries, and {@link #parse(byte[])} is the only supported entry point.</p>
 *
 * <p>The parser walks the input buffer once and pushes packed tape entries describing the
 * depth-first iteration order of the tree. Open containers are tracked on a fixed-capacity
 * 512-frame stack so deeply nested adversarial input throws {@link NbtMaxDepthException} rather
 * than {@link StackOverflowError}.</p>
 *
 * <p>Reads use {@link NbtByteCodec}'s {@code VarHandle}-driven big-endian primitives - no
 * {@code DataInputStream} layer, no per-byte syscalls. Pointer-kind tape elements record buffer
 * offsets pointing at the wire length prefix (2 bytes for strings / keys, 4 bytes for arrays);
 * 8-byte primitives that do not fit in the 56-bit inline payload also become {@code *_PTR}
 * entries.</p>
 *
 * <p>The retained buffer in the produced {@link Tape} is the caller's input array, by reference -
 * callers must not mutate it. {@link NbtFactory#borrowFromByteArray
 * NbtFactory.borrowFromByteArray} owns the lifetime of the buffer.</p>
 */
public class NbtInputTape implements NbtInput {

    /**
     * Maximum nesting depth for open containers. Matches the 512 cap on every other deserializer
     * in the codebase.
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

    // ------------------------------------------------------------------
    // Tape-builder state. Reset on each readCompoundTag / readListTag call.
    // ------------------------------------------------------------------

    private long @NotNull [] tape;

    private int tapeSize;

    private final int @NotNull [] openFrameTapeIndex;

    private final int @NotNull [] openFrameRemaining;

    private final int @NotNull [] openFrameCompoundEntries;

    private final byte @NotNull [] openFrameListElementId;

    private int sp;

    public NbtInputTape(byte @NotNull [] input) {
        this.input = input;
        this.position = 0;
        this.tape = new long[Math.max(16, input.length * 2)];
        this.tapeSize = 0;
        this.openFrameTapeIndex = new int[MAX_DEPTH];
        this.openFrameRemaining = new int[MAX_DEPTH];
        this.openFrameCompoundEntries = new int[MAX_DEPTH];
        this.openFrameListElementId = new byte[MAX_DEPTH];
        this.sp = -1;
    }

    // ------------------------------------------------------------------
    // Static convenience for callers that want the raw Tape (e.g. tests).
    // ------------------------------------------------------------------

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
     *     {@value #MAX_DEPTH} levels of nesting, or a tape size that overflows the 24-bit
     *     {@code endOffset} field
     */
    public static @NotNull Tape parse(byte @NotNull [] input) throws IOException {
        if (input.length < 3)
            throw new NbtFormatException("Buffer too short for an NBT root (need at least 3 bytes, got %d)", input.length);

        NbtInputTape in = new NbtInputTape(input);
        byte rootType = in.readByte();

        if (rootType != TagType.COMPOUND.getId())
            throw new NbtFormatException("Root tag must be TAG_Compound, found id %d", rootType & 0xFF);

        int rootNameLen = in.readUnsignedShort();
        in.advance(rootNameLen);

        in.buildCompoundBodyIntoTape();
        return new Tape(in.tape, in.tapeSize, input);
    }

    // ------------------------------------------------------------------
    // NbtInput primitive reads. Truncated input surfaces as the unchecked
    // NbtFormatException with location info - more useful than the bare
    // EOFException NbtInputBuffer raises, and unchecked so it flies through
    // the NbtInput {@code throws IOException} contract unwrapped.
    // ------------------------------------------------------------------

    @Override
    public boolean readBoolean() {
        this.requireRemaining(1);
        return this.input[this.position++] != 0;
    }

    @Override
    public byte readByte() {
        this.requireRemaining(1);
        return this.input[this.position++];
    }

    @Override
    public short readShort() {
        this.requireRemaining(2);
        short v = NbtByteCodec.getShort(this.input, this.position);
        this.position += 2;
        return v;
    }

    private int readUnsignedShort() {
        this.requireRemaining(2);
        int v = NbtByteCodec.getUnsignedShort(this.input, this.position);
        this.position += 2;
        return v;
    }

    @Override
    public int readInt() {
        this.requireRemaining(4);
        int v = NbtByteCodec.getInt(this.input, this.position);
        this.position += 4;
        return v;
    }

    @Override
    public long readLong() {
        this.requireRemaining(8);
        long v = NbtByteCodec.getLong(this.input, this.position);
        this.position += 8;
        return v;
    }

    @Override
    public float readFloat() {
        return Float.intBitsToFloat(this.readInt());
    }

    @Override
    public double readDouble() {
        return Double.longBitsToDouble(this.readLong());
    }

    @Override
    public @NotNull String readUTF() throws IOException {
        int utfLen = this.readUnsignedShort();
        this.requireRemaining(utfLen);
        String value = NbtModifiedUtf8.decode(this.input, this.position, utfLen);
        this.position += utfLen;
        return value;
    }

    @Override
    public byte @NotNull [] readByteArray() {
        int length = this.readInt();
        this.requireRemaining(length);
        byte[] data = new byte[length];
        System.arraycopy(this.input, this.position, data, 0, length);
        this.position += length;
        return data;
    }

    @Override
    public int @NotNull [] readIntArray() {
        int length = this.readInt();
        this.requireRemainingBytes((long) length << 2);
        int[] data = new int[length];
        NbtByteCodec.getIntArrayBE(this.input, this.position, data, 0, length);
        this.position += length << 2;
        return data;
    }

    @Override
    public long @NotNull [] readLongArray() {
        int length = this.readInt();
        this.requireRemainingBytes((long) length << 3);
        long[] data = new long[length];
        NbtByteCodec.getLongArrayBE(this.input, this.position, data, 0, length);
        this.position += length << 3;
        return data;
    }

    private void advance(int byteCount) {
        if (byteCount < 0)
            throw new NbtFormatException("Negative advance %d at offset %d", byteCount, this.position);

        this.requireRemaining(byteCount);
        this.position += byteCount;
    }

    private void requireRemaining(int byteCount) {
        if (this.position + byteCount > this.input.length)
            throw new NbtFormatException(
                "Truncated NBT input - need %d bytes at offset %d, only %d available",
                byteCount, this.position, this.input.length - this.position);
    }

    /**
     * {@code long}-arithmetic overload for the bulk-array readers - {@code length << 2/3} can
     * overflow {@code int} on pathological array lengths.
     */
    private void requireRemainingBytes(long byteCount) {
        if (this.position + byteCount > this.input.length)
            throw new NbtFormatException(
                "Truncated NBT input - need %d bytes at offset %d, only %d available",
                byteCount, this.position, this.input.length - this.position);
    }

    // ------------------------------------------------------------------
    // Tape builder. The driveStack loop and per-kind dispatch are lifted
    // verbatim from the prior TapeParser - this is the same shape, just
    // exposed through the NbtInput contract.
    // ------------------------------------------------------------------

    private void buildCompoundBodyIntoTape() {
        int rootHeaderIdx = this.appendTape(TapeElement.packCompoundHeader(0, 0));
        this.openFrame(rootHeaderIdx, COMPOUND_FRAME, (byte) 0);
        this.driveStack();

        if (this.sp != -1)
            throw new NbtFormatException("Parser ended with %d open frames remaining", this.sp + 1);
    }

    private void driveStack() {
        while (this.sp >= 0) {
            int remaining = this.openFrameRemaining[this.sp];

            if (remaining == COMPOUND_FRAME) {
                this.stepCompound();
                continue;
            }

            this.stepList(remaining);
        }
    }

    private void stepCompound() {
        byte typeId = this.readByte();

        if (typeId == TagType.END.getId()) {
            this.closeCompoundFrame();
            return;
        }

        int keyOffset = this.position;
        int keyLen = this.readUnsignedShort();
        this.advance(keyLen);

        this.appendTape(TapeElement.pack(TapeKind.KEY_PTR, keyOffset));
        this.openFrameCompoundEntries[this.sp]++;

        this.dispatchValue(typeId);
    }

    private void stepList(int remaining) {
        if (remaining == 0) {
            this.closeListFrame();
            return;
        }

        this.openFrameRemaining[this.sp] = remaining - 1;
        this.dispatchValue(this.openFrameListElementId[this.sp]);
    }

    private void dispatchValue(byte typeId) {
        switch (typeId) {
            case 1 -> {
                byte v = this.readByte();
                this.appendTape(TapeElement.pack(TapeKind.BYTE_INLINE, v));
            }
            case 2 -> {
                short v = this.readShort();
                this.appendTape(TapeElement.pack(TapeKind.SHORT_INLINE, v));
            }
            case 3 -> {
                int v = this.readInt();
                this.appendTape(TapeElement.pack(TapeKind.INT_INLINE, v));
            }
            case 4 -> {
                int offset = this.position;
                this.advance(8);
                this.appendTape(TapeElement.pack(TapeKind.LONG_PTR, offset));
            }
            case 5 -> {
                int bits = this.readInt();
                this.appendTape(TapeElement.pack(TapeKind.FLOAT_INLINE, bits));
            }
            case 6 -> {
                int offset = this.position;
                this.advance(8);
                this.appendTape(TapeElement.pack(TapeKind.DOUBLE_PTR, offset));
            }
            case 7 -> {
                int offset = this.position;
                int len = this.readInt();
                this.advance(len);
                this.appendTape(TapeElement.pack(TapeKind.BYTE_ARRAY_PTR, offset));
            }
            case 8 -> {
                int offset = this.position;
                int len = this.readUnsignedShort();
                this.advance(len);
                this.appendTape(TapeElement.pack(TapeKind.STRING_PTR, offset));
            }
            case 9 -> this.openListValue();
            case 10 -> this.openCompoundValue();
            case 11 -> {
                int offset = this.position;
                int len = this.readInt();
                this.advance(Math.multiplyExact(len, 4));
                this.appendTape(TapeElement.pack(TapeKind.INT_ARRAY_PTR, offset));
            }
            case 12 -> {
                int offset = this.position;
                int len = this.readInt();
                this.advance(Math.multiplyExact(len, 8));
                this.appendTape(TapeElement.pack(TapeKind.LONG_ARRAY_PTR, offset));
            }
            default -> throw new NbtFormatException("Unknown tag id encountered while parsing buffer: %d", typeId & 0xFF);
        }
    }

    private void openCompoundValue() {
        int headerIdx = this.appendTape(TapeElement.packCompoundHeader(0, 0));
        this.openFrame(headerIdx, COMPOUND_FRAME, (byte) 0);
    }

    private void openListValue() {
        byte elementType = this.readByte();
        int rawLength = this.readInt();
        int length = Math.max(0, rawLength);

        int headerIdx = this.appendTape(TapeElement.packListHeader(elementType, length, 0));
        this.openFrame(headerIdx, length, elementType);
    }

    private void openFrame(int headerTapeIdx, int remaining, byte elementId) {
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
        byte elementId = TapeElement.unpackListElementId(this.tape[headerIdx]);
        int approxLen = TapeElement.unpackApproxLen(this.tape[headerIdx]);
        this.sp--;

        int endIdx = this.appendTape(TapeElement.pack(TapeKind.LIST_END, headerIdx));
        this.checkEndOffsetFits(endIdx);
        this.tape[headerIdx] = TapeElement.packListHeader(elementId, approxLen, endIdx);
    }

    private void checkEndOffsetFits(int endIdx) {
        if (endIdx > TapeElement.MAX_END_OFFSET)
            throw new NbtFormatException(
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

}
