package lib.minecraft.nbt.tags.borrow;

import lib.minecraft.nbt.exception.NbtFormatException;
import lib.minecraft.nbt.io.util.NbtModifiedUtf8;
import lib.minecraft.nbt.tags.Tag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * {@link Map} view over a {@link TapeKind#COMPOUND_HEADER}-bracketed tape range that decodes
 * entries lazily on a per-key basis.
 *
 * <p>{@link #containsKey(Object)} and {@link #get(Object)} run a linear scan via
 * {@link Tape#findChildTapeIndex(int, String)}. Touching a single field on a 60-entry compound
 * costs at most 60 key-byte compares; touching all 60 fields costs the same as materializing the
 * whole map up front, plus the per-tag wrapper allocations.</p>
 *
 * <p>Mutation paths ({@link #put(String, Tag)}, {@link #remove(Object)}, {@link #clear()}) promote
 * the view to a {@link LinkedHashMap} on first call. Promotion copies every entry from the tape
 * into the map, after which the view forwards all subsequent operations to the promoted map and
 * no further tape decode happens. The original tape reference is retained so any borrowed-tag
 * children still hold valid pointers into the buffer.</p>
 *
 * <p>{@link #entrySet()} returns a lazy {@link TapeEntrySet} that walks the tape per
 * {@link Iterator#next() next()} call without forcing promotion - iteration over a 60-entry
 * borrowed compound visits 60 tape slots and constructs 60 borrowed-tag navigators, with no
 * intermediate {@link LinkedHashMap}. Mutation through the entry-set (i.e. {@code remove()} on
 * the iterator) promotes-and-forwards just like the other mutation entry points; after that
 * {@code entrySet()} returns the promoted map's own entrySet so the standard {@code Map} contract
 * holds.</p>
 */
@ApiStatus.Internal
final class TapeMapView extends AbstractMap<String, Tag<?>> {

    private final @NotNull Tape tape;

    private final int tapeIndex;

    private final int endIdx;

    private final int approxLen;

    private @Nullable LinkedHashMap<String, Tag<?>> promoted;

    TapeMapView(@NotNull Tape tape, int tapeIndex) {
        long header = tape.elementAt(tapeIndex);
        this.tape = tape;
        this.tapeIndex = tapeIndex;
        this.endIdx = TapeElement.unpackEndOffset(header);
        this.approxLen = TapeElement.unpackApproxLen(header);
    }

    @Override
    public int size() {
        if (this.promoted != null) return this.promoted.size();

        int count = 0;
        int idx = this.tapeIndex + 1;

        while (idx < this.endIdx) {
            int valueIdx = idx + 1;
            idx = this.tape.nextSibling(valueIdx);
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
    public boolean containsKey(Object key) {
        if (this.promoted != null) return this.promoted.containsKey(key);
        if (!(key instanceof String name)) return false;
        return this.tape.findChildTapeIndex(this.tapeIndex, name) != Tape.NOT_FOUND;
    }

    @Override
    public @Nullable Tag<?> get(Object key) {
        if (this.promoted != null) return this.promoted.get(key);
        if (!(key instanceof String name)) return null;
        int idx = this.tape.findChildTapeIndex(this.tapeIndex, name);
        if (idx == Tape.NOT_FOUND) return null;
        return this.tape.tagAt(idx);
    }

    @Override
    public @Nullable Tag<?> put(String key, Tag<?> value) {
        return this.ensureMaterialized().put(key, value);
    }

    @Override
    public @Nullable Tag<?> remove(Object key) {
        return this.ensureMaterialized().remove(key);
    }

    @Override
    public void clear() {
        if (this.promoted == null)
            this.promoted = LinkedHashMap.newLinkedHashMap(this.approxLen);
        else
            this.promoted.clear();
    }

    @Override
    public @NotNull Set<Map.Entry<String, Tag<?>>> entrySet() {
        if (this.promoted != null) return this.promoted.entrySet();
        return new TapeEntrySet();
    }

    private @NotNull LinkedHashMap<String, Tag<?>> ensureMaterialized() {
        if (this.promoted != null) return this.promoted;

        LinkedHashMap<String, Tag<?>> map = LinkedHashMap.newLinkedHashMap(this.approxLen);
        int idx = this.tapeIndex + 1;

        while (idx < this.endIdx) {
            long keyElement = this.tape.elementAt(idx);

            if (TapeElement.unpackKind(keyElement) != TapeKind.KEY_PTR)
                throw new NbtFormatException(
                    "Expected KEY_PTR inside compound at tape index %d, found %s",
                    idx, TapeElement.unpackKind(keyElement));

            int keyOffset = (int) TapeElement.unpackValue(keyElement);
            String name = NbtModifiedUtf8.decode(this.tape.buffer(), keyOffset);
            int valueIdx = idx + 1;
            Tag<?> value = this.tape.tagAt(valueIdx);
            map.put(name, value);
            idx = this.tape.nextSibling(valueIdx);
        }

        this.promoted = map;
        return map;
    }

    /**
     * Lazy entrySet that walks the tape per {@code next()} call. The set holds no state itself;
     * its iterator carries the cursor.
     */
    private final class TapeEntrySet extends AbstractSet<Map.Entry<String, Tag<?>>> {

        @Override
        public int size() {
            return TapeMapView.this.size();
        }

        @Override
        public boolean isEmpty() {
            return TapeMapView.this.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry<?, ?> entry)) return false;
            Object key = entry.getKey();
            if (!(key instanceof String name)) return false;
            Tag<?> value = TapeMapView.this.get(name);
            if (value == null) return false;
            Object other = entry.getValue();
            return value.equals(other);
        }

        @Override
        public boolean remove(Object o) {
            if (!(o instanceof Map.Entry<?, ?> entry)) return false;
            Object key = entry.getKey();
            if (!(key instanceof String name)) return false;
            return TapeMapView.this.ensureMaterialized().entrySet().remove(entry);
        }

        @Override
        public void clear() {
            TapeMapView.this.clear();
        }

        @Override
        public @NotNull Iterator<Map.Entry<String, Tag<?>>> iterator() {
            // Promotion that happens mid-iteration must not break iteration semantics. Snapshot
            // the promoted reference up front: if it was null at iterator construction, we walk
            // the tape; if it gets set later by an unrelated mutation, our iterator keeps using
            // the tape and the resulting view reflects the pre-mutation state. This matches the
            // ConcurrentModificationException-free contract a typical Map.entrySet().iterator()
            // wouldn't offer, which is fine for the borrow read-mostly workload.
            if (TapeMapView.this.promoted != null)
                return TapeMapView.this.promoted.entrySet().iterator();

            return new TapeEntryIterator();
        }

    }

    private final class TapeEntryIterator implements Iterator<Map.Entry<String, Tag<?>>> {

        private int cursor = TapeMapView.this.tapeIndex + 1;

        @Override
        public boolean hasNext() {
            return this.cursor < TapeMapView.this.endIdx;
        }

        @Override
        public Map.Entry<String, Tag<?>> next() {
            if (this.cursor >= TapeMapView.this.endIdx)
                throw new NoSuchElementException();

            Tape tape = TapeMapView.this.tape;
            long keyElement = tape.elementAt(this.cursor);

            if (TapeElement.unpackKind(keyElement) != TapeKind.KEY_PTR)
                throw new NbtFormatException(
                    "Expected KEY_PTR inside compound at tape index %d, found %s",
                    this.cursor, TapeElement.unpackKind(keyElement));

            int keyOffset = (int) TapeElement.unpackValue(keyElement);
            String name = NbtModifiedUtf8.decode(tape.buffer(), keyOffset);
            int valueIdx = this.cursor + 1;
            Tag<?> value = tape.tagAt(valueIdx);
            this.cursor = tape.nextSibling(valueIdx);
            return Map.entry(name, value);
        }

    }

}
