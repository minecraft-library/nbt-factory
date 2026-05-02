package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.tags.Tag;
import lib.minecraft.nbt.tags.TagType;
import lib.minecraft.nbt.tags.collection.ListTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Borrowed view over a {@link TapeKind#LIST_HEADER}-bracketed tape range.
 *
 * <p>The packed list header carries the wire {@code elementId} (NBT tag id of the list's
 * elements), an approximate length (saturated at {@link TapeElement#MAX_APPROX_LEN}), and the
 * tape index of the matching {@link TapeKind#LIST_END}. Random access via {@link #get(int)} is
 * an O(N) walk - the tape does not store per-element offsets, mirroring simdnbt's
 * {@code borrow/list.rs} which makes the same tradeoff. Sequential traversal via
 * {@link #iterator()} is O(N) for the whole list (each step is amortized O(1)).</p>
 */
@ApiStatus.Experimental
public final class BorrowedListTag implements BorrowedTag<List<Tag<?>>> {

    private final @NotNull Tape tape;

    private final int tapeIndex;

    BorrowedListTag(@NotNull Tape tape, int tapeIndex) {
        this.tape = tape;
        this.tapeIndex = tapeIndex;
    }

    /**
     * Returns the wire element-id - the NBT tag id all entries of this list carry on the wire
     * (e.g. {@code 1} for byte lists, {@code 10} for compound lists). Preserved by the encoder
     * even on empty lists, where the element type would otherwise be unrecoverable from the
     * absent children.
     *
     * @return the wire element-id byte
     */
    public byte getElementId() {
        return TapeElement.unpackListElementId(this.tape.elementAt(this.tapeIndex));
    }

    /**
     * Number of elements in this list. Walks the tape from the header to the matching
     * {@code LIST_END} via {@link Tape#nextSibling(int)} - O(N) over the element count.
     *
     * @return the element count
     */
    public int size() {
        long header = this.tape.elementAt(this.tapeIndex);
        int endIdx = TapeElement.unpackEndOffset(header);
        int idx = this.tapeIndex + 1;
        int count = 0;

        while (idx < endIdx) {
            idx = this.tape.nextSibling(idx);
            count++;
        }

        return count;
    }

    /**
     * True when the list has no elements. Cheaper than {@link #size()} - reads the next slot once.
     *
     * @return whether the list is empty
     */
    public boolean isEmpty() {
        long header = this.tape.elementAt(this.tapeIndex);
        return this.tapeIndex + 1 >= TapeElement.unpackEndOffset(header);
    }

    /**
     * Returns the {@code i}-th element. <b>O(N)</b> - the tape does not store per-element
     * offsets, so this walks {@code i + 1} {@link Tape#nextSibling(int)} steps from the header.
     *
     * @param i element index, {@code 0..size()-1}
     * @return a borrowed view of the element
     * @throws IndexOutOfBoundsException if {@code i} is out of range
     */
    public @NotNull BorrowedTag<?> get(int i) {
        if (i < 0)
            throw new IndexOutOfBoundsException("index must be non-negative: " + i);

        long header = this.tape.elementAt(this.tapeIndex);
        int endIdx = TapeElement.unpackEndOffset(header);
        int idx = this.tapeIndex + 1;
        int cursor = 0;

        while (idx < endIdx) {
            if (cursor == i)
                return BorrowedTag.fromTape(this.tape, idx);

            idx = this.tape.nextSibling(idx);
            cursor++;
        }

        throw new IndexOutOfBoundsException("index out of range: " + i);
    }

    /**
     * Returns the {@code i}-th element as a zero-decode {@link MutfStringView} when the element
     * is a {@link BorrowedStringTag}.
     *
     * <p>Returns {@code null} if the element exists but is not a string. Mirrors
     * {@link BorrowedCompoundTag#getStringView(String)}: callers that only need to compare or
     * hash the value avoid the {@link MutfStringView#toString()} allocation by going through
     * {@link MutfStringView#equalsString(String)} or {@link MutfStringView#hashCode()}.</p>
     *
     * @param i element index, {@code 0..size()-1}
     * @return a zero-decode view of the string value, or {@code null} if not a string
     * @throws IndexOutOfBoundsException if {@code i} is out of range
     * @see BorrowedCompoundTag#getStringView(String)
     */
    public @Nullable MutfStringView getStringView(int i) {
        if (i < 0)
            throw new IndexOutOfBoundsException("index must be non-negative: " + i);

        long header = this.tape.elementAt(this.tapeIndex);
        int endIdx = TapeElement.unpackEndOffset(header);
        int idx = this.tapeIndex + 1;
        int cursor = 0;

        while (idx < endIdx) {
            if (cursor == i) {
                long element = this.tape.elementAt(idx);

                if (TapeElement.unpackKind(element) != TapeKind.STRING_PTR)
                    return null;

                int tagOffset = (int) TapeElement.unpackValue(element);
                return MutfStringView.fromTagOffset(this.tape.buffer(), tagOffset);
            }

            idx = this.tape.nextSibling(idx);
            cursor++;
        }

        throw new IndexOutOfBoundsException("index out of range: " + i);
    }

    /**
     * Lazy iterator walking the tape from the list header to the matching {@code LIST_END}.
     * Allocates a navigator per element (no eager materialization).
     *
     * @return the iterator
     */
    public @NotNull Iterator<BorrowedTag<?>> iterator() {
        long header = this.tape.elementAt(this.tapeIndex);
        int endIdx = TapeElement.unpackEndOffset(header);
        return new ListIterator(endIdx);
    }

    @Override
    public byte getId() {
        return TagType.LIST.getId();
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public @NotNull ListTag materialize() {
        long header = this.tape.elementAt(this.tapeIndex);
        byte elementId = TapeElement.unpackListElementId(header);
        int approxLen = TapeElement.unpackApproxLen(header);
        // Mirror Tape.readList - pre-seed the elementId so empty lists round-trip with the same
        // wire type the materializing parser would produce.
        ListTag list = new ListTag(elementId, approxLen);

        int endIdx = TapeElement.unpackEndOffset(header);
        int idx = this.tapeIndex + 1;

        while (idx < endIdx) {
            BorrowedTag<?> element = BorrowedTag.fromTape(this.tape, idx);
            list.add(element.materialize());
            idx = this.tape.nextSibling(idx);
        }

        return list;
    }

    private final class ListIterator implements Iterator<BorrowedTag<?>> {

        private final int endIdx;

        private int cursor;

        ListIterator(int endIdx) {
            this.endIdx = endIdx;
            this.cursor = BorrowedListTag.this.tapeIndex + 1;
        }

        @Override
        public boolean hasNext() {
            return this.cursor < this.endIdx;
        }

        @Override
        public BorrowedTag<?> next() {
            if (this.cursor >= this.endIdx)
                throw new NoSuchElementException();

            BorrowedTag<?> tag = BorrowedTag.fromTape(BorrowedListTag.this.tape, this.cursor);
            this.cursor = BorrowedListTag.this.tape.nextSibling(this.cursor);
            return tag;
        }

    }

}
