package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.exception.NbtException;
import lib.minecraft.nbt.tags.Tag;
import lib.minecraft.nbt.tags.TagType;
import lib.minecraft.nbt.tags.collection.CompoundTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Borrowed view over a {@link TapeKind#COMPOUND_HEADER}-bracketed tape range.
 *
 * <p>{@link #get(String)} delegates to {@link Tape#findChildTapeIndex(int, String)} - a linear
 * scan with O(1) skip-past-subtree on each child via the header's {@code endOffset}. Matches
 * simdnbt's deliberate design ({@code borrow/compound.rs:73-82}); a hash-indexed lookup would
 * pay an allocation up front that most lookups never amortize. {@link #size()} is also O(N).</p>
 *
 * <p>Iteration via {@link #entries()} preserves insertion order (the order the encoder/parser
 * wrote keys onto the tape, which itself matches the materializing path). Each {@code Map.Entry}
 * is freshly allocated per-element - the plan flags this as a perf concern but C3 does not
 * optimize it.</p>
 */
@ApiStatus.Experimental
public final class BorrowedCompoundTag implements BorrowedTag<Map<String, Tag<?>>> {

    private final @NotNull Tape tape;

    private final int tapeIndex;

    BorrowedCompoundTag(@NotNull Tape tape, int tapeIndex) {
        this.tape = tape;
        this.tapeIndex = tapeIndex;
    }

    /**
     * Number of entries in this compound. Walks the tape from the header to the matching
     * {@code COMPOUND_END} via {@link Tape#nextSibling(int)} - O(N) over the entry count.
     *
     * @return the entry count
     */
    public int size() {
        long header = this.tape.elementAt(this.tapeIndex);
        int endIdx = TapeElement.unpackEndOffset(header);
        int idx = this.tapeIndex + 1;
        int count = 0;

        while (idx < endIdx) {
            // Each entry is one KEY_PTR followed by a value (whose subtree nextSibling skips
            // past in O(1) for containers).
            int valueIdx = idx + 1;
            idx = this.tape.nextSibling(valueIdx);
            count++;
        }

        return count;
    }

    /**
     * True when the compound has no entries.
     *
     * @return whether the compound is empty
     */
    public boolean isEmpty() {
        long header = this.tape.elementAt(this.tapeIndex);
        return this.tapeIndex + 1 >= TapeElement.unpackEndOffset(header);
    }

    /**
     * Looks up the entry whose key equals {@code name}. Returns {@code null} if absent.
     *
     * @param name the key to look up
     * @return a borrowed view of the value, or {@code null} if no entry matches
     */
    public @Nullable BorrowedTag<?> get(@NotNull String name) {
        int valueIdx = this.tape.findChildTapeIndex(this.tapeIndex, name);

        if (valueIdx == Tape.NOT_FOUND)
            return null;

        return BorrowedTag.fromTape(this.tape, valueIdx);
    }

    /**
     * Returns true when an entry with the given key exists.
     *
     * @param name the key to look up
     * @return whether the entry exists
     */
    public boolean containsKey(@NotNull String name) {
        return this.tape.findChildTapeIndex(this.tapeIndex, name) != Tape.NOT_FOUND;
    }

    /**
     * Returns a lazy iterable over the {@code (key, value)} entries in insertion order. Each
     * iteration step decodes the key string and constructs a fresh navigator for the value.
     *
     * @return the entry iterable
     */
    public @NotNull Iterable<Map.Entry<String, BorrowedTag<?>>> entries() {
        return EntryIterator::new;
    }

    @Override
    public byte getId() {
        return TagType.COMPOUND.getId();
    }

    @Override
    public @NotNull CompoundTag materialize() {
        long header = this.tape.elementAt(this.tapeIndex);
        int endIdx = TapeElement.unpackEndOffset(header);
        int approxLen = TapeElement.unpackApproxLen(header);
        // Pre-size to the encoder's saturated approxLen - matches the materializing path's
        // CompoundTag(int) constructor selection (small mode under SMALL_CAPACITY, map mode
        // above).
        CompoundTag compound = new CompoundTag(approxLen);

        int idx = this.tapeIndex + 1;

        while (idx < endIdx) {
            long keyElement = this.tape.elementAt(idx);

            if (TapeElement.unpackKind(keyElement) != TapeKind.KEY_PTR)
                throw new NbtException(
                    "Expected KEY_PTR inside compound at tape index %d, found %s",
                    idx, TapeElement.unpackKind(keyElement));

            int keyOffset = (int) TapeElement.unpackValue(keyElement);
            String key = BorrowedTagSupport.decodeUtf8At(this.tape.buffer(), keyOffset);
            int valueIdx = idx + 1;
            BorrowedTag<?> value = BorrowedTag.fromTape(this.tape, valueIdx);
            compound.put(key, value.materialize());
            idx = this.tape.nextSibling(valueIdx);
        }

        return compound;
    }

    private final class EntryIterator implements Iterator<Map.Entry<String, BorrowedTag<?>>> {

        private final int endIdx;

        private int cursor;

        EntryIterator() {
            long header = BorrowedCompoundTag.this.tape.elementAt(BorrowedCompoundTag.this.tapeIndex);
            this.endIdx = TapeElement.unpackEndOffset(header);
            this.cursor = BorrowedCompoundTag.this.tapeIndex + 1;
        }

        @Override
        public boolean hasNext() {
            return this.cursor < this.endIdx;
        }

        @Override
        public Map.Entry<String, BorrowedTag<?>> next() {
            if (this.cursor >= this.endIdx)
                throw new NoSuchElementException();

            Tape tape = BorrowedCompoundTag.this.tape;
            long keyElement = tape.elementAt(this.cursor);

            if (TapeElement.unpackKind(keyElement) != TapeKind.KEY_PTR)
                throw new NbtException(
                    "Expected KEY_PTR inside compound at tape index %d, found %s",
                    this.cursor, TapeElement.unpackKind(keyElement));

            int keyOffset = (int) TapeElement.unpackValue(keyElement);
            String key = BorrowedTagSupport.decodeUtf8At(tape.buffer(), keyOffset);
            int valueIdx = this.cursor + 1;
            BorrowedTag<?> value = BorrowedTag.fromTape(tape, valueIdx);
            this.cursor = tape.nextSibling(valueIdx);
            return Map.entry(key, value);
        }

    }

}
