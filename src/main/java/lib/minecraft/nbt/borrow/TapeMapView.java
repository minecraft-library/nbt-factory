package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.exception.NbtFormatException;
import lib.minecraft.nbt.tags.Tag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * {@link Map} view over a {@link TapeKind#COMPOUND_HEADER}-bracketed tape range that decodes
 * entries lazily on a per-key basis.
 *
 * <p>{@link #containsKey(Object)} and {@link #get(Object)} run a linear scan via
 * {@link Tape#findChildTapeIndex(int, String)} - the same shape as simdnbt's borrow path. Touching
 * a single field on a 60-entry compound costs at most 60 key-byte compares; touching all 60 fields
 * costs the same as materializing the whole map up front, plus the per-tag wrapper allocations.</p>
 *
 * <p>Mutation paths ({@link #put(String, Tag)}, {@link #remove(Object)}, {@link #clear()}) promote
 * the view to a {@link LinkedHashMap} on first call. Promotion copies every entry from the tape
 * into the map, after which the view forwards all subsequent operations to the promoted map and
 * no further tape decode happens. The original tape reference is retained so any borrowed-tag
 * children still hold valid pointers into the buffer.</p>
 *
 * <p>{@link #entrySet()} forces promotion - the entry-set view must reflect mutations made
 * through it, and the cheapest contract-correct implementation is to materialize once and return
 * the promoted map's entrySet.</p>
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
        return BorrowedTag.fromTape(this.tape, idx);
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
        return this.ensureMaterialized().entrySet();
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
            String name = BorrowedTagSupport.decodeUtf8At(this.tape.buffer(), keyOffset);
            int valueIdx = idx + 1;
            Tag<?> value = BorrowedTag.fromTape(this.tape, valueIdx);
            map.put(name, value);
            idx = this.tape.nextSibling(valueIdx);
        }

        this.promoted = map;
        return map;
    }

}
