package lib.minecraft.nbt.tags.borrow;

import lib.minecraft.nbt.tags.Tag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * {@link List} view over a {@link TapeKind#LIST_HEADER}-bracketed tape range that decodes
 * elements lazily on a per-index basis.
 *
 * <p>{@link #get(int)} runs an O(i) walk via {@link Tape#nextSibling(int)} - the tape does not
 * store per-element offsets. Sequential traversal via {@link #iterator()} amortizes to O(1) per
 * step.</p>
 *
 * <p>Mutation paths promote the view to an {@link ArrayList} on first call by walking the entire
 * tape range. Subsequent operations forward to the promoted list.</p>
 */
@ApiStatus.Internal
final class TapeListView extends AbstractList<Tag<?>> {

    private final @NotNull Tape tape;

    private final int tapeIndex;

    private final int endIdx;

    private final int approxLen;

    private @Nullable ArrayList<Tag<?>> promoted;

    TapeListView(@NotNull Tape tape, int tapeIndex) {
        long header = tape.elementAt(tapeIndex);
        this.tape = tape;
        this.tapeIndex = tapeIndex;
        this.endIdx = TapeElement.unpackEndOffset(header);
        this.approxLen = TapeElement.unpackApproxLen(header);
    }

    @Override
    public int size() {
        if (this.promoted != null) return this.promoted.size();

        int idx = this.tapeIndex + 1;
        int count = 0;

        while (idx < this.endIdx) {
            idx = this.tape.nextSibling(idx);
            count++;
        }

        return count;
    }

    @Override
    public boolean isEmpty() {
        if (this.promoted != null) return this.promoted.isEmpty();
        return this.tapeIndex + 1 >= this.endIdx;
    }

    @Override
    public @NotNull Tag<?> get(int i) {
        if (this.promoted != null) return this.promoted.get(i);
        if (i < 0)
            throw new IndexOutOfBoundsException("index must be non-negative: " + i);

        int idx = this.tapeIndex + 1;
        int cursor = 0;

        while (idx < this.endIdx) {
            if (cursor == i)
                return this.tape.tagAt(idx);

            idx = this.tape.nextSibling(idx);
            cursor++;
        }

        throw new IndexOutOfBoundsException("index out of range: " + i);
    }

    @Override
    public @NotNull Iterator<Tag<?>> iterator() {
        if (this.promoted != null) return this.promoted.iterator();
        return new TapeIterator();
    }

    @Override
    public boolean add(Tag<?> element) {
        return this.ensureMaterialized().add(element);
    }

    @Override
    public void add(int index, Tag<?> element) {
        this.ensureMaterialized().add(index, element);
    }

    @Override
    public @NotNull Tag<?> remove(int index) {
        return this.ensureMaterialized().remove(index);
    }

    @Override
    public @NotNull Tag<?> set(int index, Tag<?> element) {
        return this.ensureMaterialized().set(index, element);
    }

    @Override
    public void clear() {
        if (this.promoted == null)
            this.promoted = new ArrayList<>(this.approxLen);
        else
            this.promoted.clear();
    }

    private @NotNull ArrayList<Tag<?>> ensureMaterialized() {
        if (this.promoted != null) return this.promoted;

        ArrayList<Tag<?>> list = new ArrayList<>(this.approxLen);
        int idx = this.tapeIndex + 1;

        while (idx < this.endIdx) {
            list.add(this.tape.tagAt(idx));
            idx = this.tape.nextSibling(idx);
        }

        this.promoted = list;
        return list;
    }

    private final class TapeIterator implements Iterator<Tag<?>> {

        private int cursor = TapeListView.this.tapeIndex + 1;

        @Override
        public boolean hasNext() {
            return this.cursor < TapeListView.this.endIdx;
        }

        @Override
        public Tag<?> next() {
            if (this.cursor >= TapeListView.this.endIdx)
                throw new NoSuchElementException();

            Tag<?> tag = TapeListView.this.tape.tagAt(this.cursor);
            this.cursor = TapeListView.this.tape.nextSibling(this.cursor);
            return tag;
        }

    }

}
