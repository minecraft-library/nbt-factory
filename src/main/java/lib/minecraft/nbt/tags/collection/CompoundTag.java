package lib.minecraft.nbt.tags.collection;

import lib.minecraft.nbt.tags.Tag;
import lib.minecraft.nbt.tags.TagType;
import lib.minecraft.nbt.tags.array.ByteArrayTag;
import lib.minecraft.nbt.tags.array.IntArrayTag;
import lib.minecraft.nbt.tags.array.LongArrayTag;
import lib.minecraft.nbt.tags.primitive.ByteTag;
import lib.minecraft.nbt.tags.primitive.DoubleTag;
import lib.minecraft.nbt.tags.primitive.FloatTag;
import lib.minecraft.nbt.tags.primitive.IntTag;
import lib.minecraft.nbt.tags.primitive.LongTag;
import lib.minecraft.nbt.tags.primitive.ShortTag;
import lib.minecraft.nbt.tags.primitive.StringTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;

/**
 * {@link TagType#COMPOUND} (ID 10) is used for storing an ordered list of key-{@link Tag value} pairs.
 *
 * <p>Internally maintains two storage modes:</p>
 * <ul>
 *   <li><b>Small mode</b> - parallel {@code String[]} keys + {@code Tag<?>[]} values arrays of
 *       capacity {@value #SMALL_CAPACITY}, scanned linearly. Most SkyBlock auction subcompounds
 *       and typical Minecraft tile/entity NBT have well under {@value #SMALL_CAPACITY} entries,
 *       so cache-friendly linear scan over a contiguous reference array beats a
 *       {@link LinkedHashMap}'s hash-and-bucket overhead. Mirrors simdnbt's deliberate choice
 *       (see {@code borrow/compound.rs}).</li>
 *   <li><b>Map mode</b> - the legacy {@link LinkedHashMap}-backed representation. Reached on
 *       the {@value #SMALL_CAPACITY}+1th distinct {@code put}, on construction with
 *       {@code expectedSize > }{@value #SMALL_CAPACITY}, or eagerly when {@link #getValue()}
 *       hands the underlying map out to a caller.</li>
 * </ul>
 *
 * <p>Promotion is one-way - once a compound is in map mode it never returns to small mode.
 * Insertion order is preserved across both modes. The public {@link Map} surface is unchanged;
 * dispatch is internal.</p>
 */
@SuppressWarnings("unchecked")
public class CompoundTag extends Tag<Map<String, Tag<?>>> implements Map<String, Tag<?>>, Iterable<Map.Entry<String, Tag<?>>> {

    /**
     * Inflection point at which small mode promotes to map mode. Sized to fit the long tail of
     * SkyBlock auction subcompounds (most are well below this) while keeping the linear-scan
     * worst case bounded - 16 byte-compares per probe is faster than the hash + bucket walk a
     * {@link LinkedHashMap} of equivalent size pays.
     */
    static final int SMALL_CAPACITY = 16;

    public static final @NotNull CompoundTag EMPTY = new CompoundTag() {
        @Override
        public void requireModifiable() {
            throw new UnsupportedOperationException("This nbt tag is not modifiable.");
        }
    };

    /**
     * Parallel-array key store for small mode. Slots {@code [0, smallSize)} are occupied;
     * {@code [smallSize, SMALL_CAPACITY)} are null. Always {@code null} once promoted.
     */
    private @Nullable String[] smallKeys;

    /**
     * Parallel-array value store for small mode. Slot semantics mirror {@link #smallKeys}.
     */
    private @Nullable Tag<?>[] smallValues;

    /**
     * Number of occupied slots in {@link #smallKeys} / {@link #smallValues}. Meaningless once
     * promoted - {@link #map()} is the source of truth in map mode.
     */
    private int smallSize;

    /**
     * Constructs an empty, unnamed compound tag in small mode.
     */
    public CompoundTag() {
        super(Collections.emptyMap());
        this.smallKeys = new String[SMALL_CAPACITY];
        this.smallValues = new Tag<?>[SMALL_CAPACITY];
    }

    /**
     * Constructs an empty, unnamed compound tag pre-sized for the given expected number of entries.
     *
     * <p>If {@code expectedSize <= }{@value #SMALL_CAPACITY} the compound starts in small mode;
     * otherwise it skips small mode and allocates a {@link LinkedHashMap} sized for
     * {@code expectedSize} directly.</p>
     *
     * @param expectedSize expected number of entries (used to choose the storage mode)
     */
    public CompoundTag(int expectedSize) {
        super(expectedSize <= SMALL_CAPACITY
            ? Collections.emptyMap()
            : LinkedHashMap.newLinkedHashMap(expectedSize));

        if (expectedSize <= SMALL_CAPACITY) {
            this.smallKeys = new String[SMALL_CAPACITY];
            this.smallValues = new Tag<?>[SMALL_CAPACITY];
        }
    }

    /**
     * Constructs a compound tag wrapping the given backing map. The compound starts in map mode -
     * the supplied map is used directly, never copied.
     *
     * @param value the tag's {@code Map<>} value.
     */
    public CompoundTag(@NotNull Map<String, Tag<?>> value) {
        super(value);
    }

    // ------------------------------------------------------------------
    // Internal mode helpers
    // ------------------------------------------------------------------

    /**
     * Returns true if this compound is currently in small (parallel-array) mode.
     *
     * <p>Package-private for tests and the iterative parser; do not expose publicly.</p>
     */
    boolean isSmallMode() {
        return this.smallValues != null;
    }

    /**
     * Returns the underlying {@link LinkedHashMap} once in map mode. Caller must guarantee small
     * mode is no longer active - this skips the small-mode null check.
     */
    private @NotNull Map<String, Tag<?>> map() {
        return super.getValue();
    }

    /**
     * Locates {@code key} in the small-mode arrays. Returns the slot index on hit, {@code -1}
     * otherwise. Caller must guarantee small mode is active.
     *
     * <p>Identity-equal-string fast path: caller-interned keys (e.g. via
     * {@link lib.minecraft.nbt.io.NbtKnownKeys NbtKnownKeys}) hit on a single reference compare
     * before falling back to {@link Objects#equals(Object, Object)}.</p>
     */
    private int smallIndexOf(@Nullable Object key) {
        String[] keys = this.smallKeys;
        int n = this.smallSize;
        for (int i = 0; i < n; i++) {
            String k = keys[i];
            if (k == key || Objects.equals(k, key))
                return i;
        }
        return -1;
    }

    /**
     * Promotes this compound from small mode to map mode. Allocates the backing
     * {@link LinkedHashMap}, copies entries in insertion order, and releases the parallel arrays.
     * Idempotent in practice - callers check {@link #isSmallMode()} first.
     *
     * @return the freshly-allocated backing map (also installed via {@link #setValue(Map)})
     */
    private @NotNull Map<String, Tag<?>> promoteToMap() {
        return this.promoteToMap(SMALL_CAPACITY * 2);
    }

    /**
     * Promotes with a caller-supplied initial capacity. Used by {@link #put(String, Tag)} on the
     * 17th distinct entry to size the new map for the entry that just triggered promotion plus
     * a little headroom.
     *
     * @param initialCapacity initial bucket capacity for the new {@link LinkedHashMap}
     * @return the freshly-allocated backing map
     */
    private @NotNull Map<String, Tag<?>> promoteToMap(int initialCapacity) {
        String[] keys = this.smallKeys;
        Tag<?>[] values = this.smallValues;
        int n = this.smallSize;
        LinkedHashMap<String, Tag<?>> promoted = LinkedHashMap.newLinkedHashMap(Math.max(initialCapacity, n + 1));
        for (int i = 0; i < n; i++)
            promoted.put(keys[i], values[i]);
        this.setValue(promoted);
        this.smallKeys = null;
        this.smallValues = null;
        this.smallSize = 0;
        return promoted;
    }

    // ------------------------------------------------------------------
    // Map<String, Tag<?>> methods
    // ------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        this.requireModifiable();
        if (this.smallValues != null) {
            for (int i = 0; i < this.smallSize; i++) {
                this.smallKeys[i] = null;
                this.smallValues[i] = null;
            }
            this.smallSize = 0;
            return;
        }
        this.map().clear();
    }

    @Override
    @SuppressWarnings("all")
    public final @NotNull CompoundTag clone() {
        // Fast path: cloning a small-mode compound stays in small mode and skips the LinkedHashMap
        // allocation entirely.
        if (this.smallValues != null) {
            CompoundTag copy = new CompoundTag();
            int n = this.smallSize;
            System.arraycopy(this.smallKeys, 0, copy.smallKeys, 0, n);
            System.arraycopy(this.smallValues, 0, copy.smallValues, 0, n);
            copy.smallSize = n;
            return copy;
        }
        Map<String, Tag<?>> source = this.map();
        LinkedHashMap<String, Tag<?>> copy = LinkedHashMap.newLinkedHashMap(source.size());
        copy.putAll(source);
        return new CompoundTag(copy);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsKey(@NotNull Object key) {
        if (this.smallValues != null)
            return this.smallIndexOf(key) >= 0;
        return this.map().containsKey(key);
    }

    public boolean containsListOf(@NotNull String key, byte of) {
        // Single probe folds the prior containsType + getListTag pair into one lookup. In small
        // mode this collapses to a linear scan that simultaneously checks key match and tag id.
        if (this.smallValues != null) {
            int idx = this.smallIndexOf(key);
            if (idx < 0) return false;
            Tag<?> tag = this.smallValues[idx];
            return tag instanceof ListTag<?> listTag && listTag.getListType() == of;
        }
        Tag<?> tag = this.map().get(key);
        return tag instanceof ListTag<?> listTag && listTag.getListType() == of;
    }

    public boolean containsType(@NotNull String key, @NotNull TagType tagType) {
        return this.containsType(key, tagType.getId());
    }

    /**
     * Returns true if this compound contains an entry with a given name (key) and if that entry is of a given tag type, false otherwise.
     *
     * @param key    the name (key) to check for.
     * @param typeId the tag type ID to test for.
     * @return true if this compound contains an entry with a given name (key) and if that entry is of a given tag type, false otherwise.
     */
    public boolean containsType(@NotNull String key, byte typeId) {
        // Single get-and-null-check replaces the prior containsKey + get pair (two hash probes).
        // In small mode the linear scan does both jobs in one pass.
        if (this.smallValues != null) {
            int idx = this.smallIndexOf(key);
            return idx >= 0 && this.smallValues[idx].getId() == typeId;
        }
        Tag<?> tag = this.map().get(key);
        return tag != null && tag.getId() == typeId;
    }

    /**
     * Checks if the path exists in the tree.
     * <p>
     * Every element of the path (except the end) are assumed to be compounds. The
     * retrieval operation will return false if any of them are missing.
     *
     * @param path The path to the entry.
     * @return True if found.
     */
    public boolean containsPath(@NotNull String path) {
        CompoundTag current = this;
        int start = 0;
        int len = path.length();

        while (true) {
            int dot = path.indexOf('.', start);
            String entry = (dot < 0) ? path.substring(start) : path.substring(start, dot);
            Tag<?> childTag = current.get(entry);

            if (childTag == null)
                return false;

            if (!(childTag instanceof CompoundTag compound))
                return true;

            if (dot < 0)
                return true;

            current = compound;
            start = dot + 1;

            if (start > len)
                return true;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsValue(@NotNull Object value) {
        boolean compareTag = value instanceof Tag<?>;

        if (this.smallValues != null) {
            for (int i = 0; i < this.smallSize; i++) {
                Tag<?> tagValue = this.smallValues[i];
                if (Objects.equals(compareTag ? tagValue : tagValue.getValue(), value))
                    return true;
            }
            return false;
        }

        for (Tag<?> tagValue : this.map().values()) {
            if (Objects.equals(compareTag ? tagValue : tagValue.getValue(), value))
                return true;
        }

        return false;
    }

    /**
     * {@inheritDoc}
     *
     * <p>In small mode this triggers permanent promotion to map mode - the returned set is a
     * live view that callers may mutate, and small mode would not see those mutations. After
     * the call this compound is in map mode for the rest of its lifetime.</p>
     */
    @Override
    public @NotNull Set<Map.Entry<String, Tag<?>>> entrySet() {
        if (this.smallValues != null)
            return this.promoteToMap().entrySet();
        return this.map().entrySet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void forEach(@NotNull Consumer<? super Entry<String, Tag<?>>> action) {
        if (this.smallValues != null) {
            String[] keys = this.smallKeys;
            Tag<?>[] values = this.smallValues;
            int n = this.smallSize;
            for (int i = 0; i < n; i++)
                action.accept(Map.entry(keys[i], values[i]));
            return;
        }
        this.map().entrySet().forEach(action);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable Tag<?> get(@Nullable Object key) {
        if (this.smallValues != null) {
            int idx = this.smallIndexOf(key);
            return idx < 0 ? null : this.smallValues[idx];
        }
        return this.map().get(key);
    }

    public <T extends Tag<?>> @NotNull T getOrDefault(@NotNull String key, @NotNull T defaultValue) {
        Tag<?> existing = this.get(key);
        return existing != null ? (T) existing : defaultValue;
    }

    @Override
    public final byte getId() {
        return TagType.COMPOUND.getId();
    }

    public <T extends Tag<?>> ListTag<T> getListTag(@NotNull String key) {
        return this.getTag(key);
    }

    /**
     * Retrieve the value of a given entry in the tree.
     * <p>
     * Every element of the path (except the end) are assumed to be compounds. The
     * retrieval operation will be cancelled if any of them are missing.
     *
     * @param path The path to the entry.
     * @return The value, or NULL if not found.
     */
    public <T extends Tag<?>> @Nullable T getPath(@NotNull String path) {
        return this.getPathOrDefault(path, null);
    }

    /**
     * Retrieve the value of a given entry in the tree.
     * <p>
     * Every element of the path (except the end) are assumed to be compounds. The
     * retrieval operation will be cancelled if any of them are missing.
     *
     * @param path The path to the entry.
     * @return The value, or default value if not found.
     */
    public <T extends Tag<?>> T getPathOrDefault(@NotNull String path, @Nullable T defaultValue) {
        if (!this.containsPath(path))
            return defaultValue;

        CompoundTag current = this;
        int start = 0;

        while (true) {
            int dot = path.indexOf('.', start);

            if (dot < 0)
                return current.getTag(path.substring(start));

            CompoundTag next = current.getTag(path.substring(start, dot));

            if (next == null)
                return defaultValue;

            current = next;
            start = dot + 1;
        }
    }

    /**
     * Retrieve the map by the given name.
     *
     * @param key The name of the map.
     * @return An existing or new map.
     */
    @SuppressWarnings("all")
    public @NotNull CompoundTag getMap(@NotNull String key) {
        return this.getMap(key, true);
    }

    /**
     * Retrieve the map by the given name.
     *
     * @param key       The name of the map.
     * @param createNew Whether or not to create a new map if its missing.
     * @return An existing map, a new map or null.
     */
    public @Nullable CompoundTag getMap(@NotNull String key, boolean createNew) {
        return this.getMap(Collections.singletonList(key), createNew);
    }

    /**
     * Retrieve a map from a given path.
     *
     * @param path      The path of compounds to look up.
     * @param createNew Whether or not to create new compounds on the way.
     * @return The map at this location.
     */
    private CompoundTag getMap(List<String> path, boolean createNew) {
        CompoundTag current = this;

        for (String entry : path) {
            CompoundTag childTag = current.getTag(entry);

            if (childTag == null) {
                if (!createNew)
                    throw new IllegalArgumentException(String.format("Cannot find '%s' in '%s'.", entry, path));

                this.requireModifiable();
                current.put(entry, childTag = new CompoundTag());
            }

            current = childTag;
        }

        return current;
    }

    /**
     * Retrieves a tag from this compound with a given name (key).
     *
     * @param key the name whose mapping is to be retrieved from this compound.
     * @param <T> the tag type you believe you are retrieving.
     * @return the value associated with {@code key} as type T.
     */
    public <T extends Tag<?>> @Nullable T getTag(@NotNull String key) {
        return (T) this.get(key);
    }

    /**
     * Retrieves a tag from this compound with a given name (key).
     *
     * @param key the name whose mapping is to be retrieved from this compound.
     * @param <T> the tag type you believe you are retrieving.
     * @return the value associated with {@code key} as type T.
     */
    public <T extends Tag<?>> @NotNull T getTagOrDefault(@NotNull String key, @NotNull T defaultValue) {
        return this.getOrDefault(key, defaultValue);
    }

    /**
     * Returns the underlying entry map.
     *
     * <p><b>Mode-switching contract</b> - in small mode this triggers permanent promotion to
     * map mode and returns the freshly-allocated backing map. The map is the live, mutable
     * representation of this compound from this call onward; mutations through it are visible
     * to subsequent {@link #get(Object)}/{@link #put(String, Tag)} calls. Promotion is one-way
     * - this compound never demotes back to small mode, even after a {@link #clear()}.</p>
     */
    @Override
    public @NotNull Map<String, Tag<?>> getValue() {
        if (this.smallValues != null)
            return this.promoteToMap();
        return super.getValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEmpty() {
        if (this.smallValues != null)
            return this.smallSize == 0;
        return this.map().isEmpty();
    }

    /**
     * {@inheritDoc}
     *
     * <p>In small mode the iterator walks the parallel arrays directly without allocating any
     * {@link Map.Entry} instances or promoting to map mode. The returned iterator does not
     * support {@code remove} in small mode.</p>
     */
    @Override
    public @NotNull Iterator<Map.Entry<String, Tag<?>>> iterator() {
        if (this.smallValues != null)
            return new SmallIterator();
        return this.map().entrySet().iterator();
    }

    /**
     * {@inheritDoc}
     *
     * <p>In small mode this triggers permanent promotion to map mode for the same reason as
     * {@link #entrySet()} - the returned set is a live mutable view.</p>
     */
    @Override
    public @NotNull Set<String> keySet() {
        if (this.smallValues != null)
            return this.promoteToMap().keySet();
        return this.map().keySet();
    }

    public boolean notEmpty() {
        return !this.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable Tag<?> put(@NotNull String key, @NotNull Tag<?> value) {
        this.requireModifiable();

        if (this.smallValues != null) {
            // Update path: existing key returns the displaced value, in-place.
            int idx = this.smallIndexOf(key);
            if (idx >= 0) {
                Tag<?> previous = this.smallValues[idx];
                this.smallValues[idx] = value;
                return previous;
            }

            // Insert path: append to the end of the parallel arrays. If we are about to overflow
            // the small-mode capacity, promote first and route the new entry through the map -
            // this preserves insertion order naturally because promoteToMap copies in order.
            if (this.smallSize == SMALL_CAPACITY) {
                Map<String, Tag<?>> promoted = this.promoteToMap(SMALL_CAPACITY * 2);
                return promoted.put(key, value);
            }

            int slot = this.smallSize;
            this.smallKeys[slot] = key;
            this.smallValues[slot] = value;
            this.smallSize = slot + 1;
            return null;
        }

        return this.map().put(key, value);
    }

    public @Nullable ByteTag put(@NotNull String key, byte value) {
        return this.putTag(key, new ByteTag(value));
    }

    public @Nullable ShortTag put(@NotNull String key, short value) {
        return this.putTag(key, new ShortTag(value));
    }

    public @Nullable IntTag put(@NotNull String key, int value) {
        return this.putTag(key, new IntTag(value));
    }

    public @Nullable LongTag put(@NotNull String key, long value) {
        return this.putTag(key, new LongTag(value));
    }

    public @Nullable FloatTag put(@NotNull String key, float value) {
        return this.putTag(key, new FloatTag(value));
    }

    public @Nullable DoubleTag put(@NotNull String key, double value) {
        return this.putTag(key, new DoubleTag(value));
    }

    public @Nullable ByteArrayTag put(@NotNull String key, byte @NotNull [] value) {
        return this.putTag(key, new ByteArrayTag(value));
    }

    public @Nullable StringTag put(@NotNull String key, @NotNull String value) {
        return this.putTag(key, new StringTag(value));
    }

    public <T extends Tag<?>> @Nullable ListTag<T> put(@NotNull String key, @NotNull ListTag<T> value) {
        return this.putTag(key, value);
    }

    public @Nullable CompoundTag put(@NotNull String key, @NotNull CompoundTag value) {
        return this.putTag(key, value);
    }

    public @Nullable IntArrayTag put(@NotNull String key, int @NotNull [] value) {
        return this.putTag(key, new IntArrayTag(value));
    }

    public @Nullable LongArrayTag put(@NotNull String key, long @NotNull [] value) {
        return this.putTag(key, new LongArrayTag(value));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putAll(@NotNull Map<? extends String, ? extends Tag<?>> map) {
        map.forEach(this::put);
    }

    /**
     * Set the value of an entry at a given location.
     * <p>
     * Every element of the path (except the end) are assumed to be compounds, and will
     * be created if they are missing.
     *
     * @param path  The path to the entry.
     * @param value The new value of this entry.
     * @return This compound, for chaining.
     */
    public @NotNull CompoundTag putPath(@NotNull String path, @NotNull Tag<?> value) {
        this.requireModifiable();
        CompoundTag current = this;
        int start = 0;

        while (true) {
            int dot = path.indexOf('.', start);

            if (dot < 0) {
                current.put(path.substring(start), value);
                return this;
            }

            String entry = path.substring(start, dot);
            CompoundTag next = current.getTag(entry);

            if (next == null)
                current.put(entry, next = new CompoundTag());

            current = next;
            start = dot + 1;
        }
    }

    public <T extends Tag<?>> @Nullable T putTag(@NotNull String key, @NotNull T value) {
        return (T) this.put(key, value);
    }

    /**
     * {@inheritDoc}
     *
     * <p>In small mode the matching slot is removed and trailing slots shift down by one to
     * preserve the contiguous {@code [0, smallSize)} occupied range and the original insertion
     * order. The shift is bounded by {@value #SMALL_CAPACITY} elements.</p>
     */
    @Override
    public @Nullable Tag<?> remove(Object key) {
        this.requireModifiable();

        if (this.smallValues != null) {
            int idx = this.smallIndexOf(key);
            if (idx < 0) return null;
            Tag<?> previous = this.smallValues[idx];
            int last = this.smallSize - 1;
            for (int i = idx; i < last; i++) {
                this.smallKeys[i] = this.smallKeys[i + 1];
                this.smallValues[i] = this.smallValues[i + 1];
            }
            this.smallKeys[last] = null;
            this.smallValues[last] = null;
            this.smallSize = last;
            return previous;
        }

        return this.map().remove(key);
    }

    /**
     * Remove the value of a given entry in the tree.
     * <p>
     * Every element of the path (except the end) are assumed to be compounds. The
     * retrieval operation will return the last most compound.
     *
     * @param path The path to the entry.
     * @return The last most compound, or this compound if not found.
     */
    public @NotNull CompoundTag removePath(@NotNull String path) {
        this.requireModifiable();
        CompoundTag current = this;
        int start = 0;

        while (true) {
            int dot = path.indexOf('.', start);

            if (dot < 0) {
                current.remove(path.substring(start));
                return current;
            }

            CompoundTag next = current.getTag(path.substring(start, dot));

            if (next == null)
                return current;

            current = next;
            start = dot + 1;
        }
    }

    public void requireModifiable() { }

    /**
     * {@inheritDoc}
     */
    public int size() {
        if (this.smallValues != null)
            return this.smallSize;
        return this.map().size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Spliterator<Map.Entry<String, Tag<?>>> spliterator() {
        if (this.smallValues != null)
            return Spliterators.spliterator(
                this.iterator(),
                this.smallSize,
                Spliterator.ORDERED | Spliterator.SIZED | Spliterator.DISTINCT);
        return this.map().entrySet().spliterator();
    }

    /**
     * {@inheritDoc}
     *
     * <p>In small mode this triggers permanent promotion to map mode for the same reason as
     * {@link #entrySet()} - the returned collection is a live mutable view.</p>
     */
    @Override
    public @NotNull Collection<Tag<?>> values() {
        if (this.smallValues != null)
            return this.promoteToMap().values();
        return this.map().values();
    }

    /**
     * Maximum tag-tree depth rendered by {@link #toString}. Beyond this, nested compounds and lists
     * collapse to {@code {...}} / {@code [...]} placeholders to keep IDE debugger evaluations fast
     * even on deep auction-style payloads.
     */
    static final int TO_STRING_MAX_DEPTH = 3;

    /**
     * Per-level entry cap rendered by {@link #toString}. Entries beyond this collapse to
     * {@code ... (N more)}.
     */
    static final int TO_STRING_MAX_ENTRIES = 50;

    /**
     * {@inheritDoc}
     *
     * <p>Computed without forcing promotion - in small mode this walks the parallel arrays
     * directly. Returns a value consistent with {@link Map#hashCode()}'s contract: the sum of
     * each entry's {@code keyHash ^ valueHash}.</p>
     */
    @Override
    public int hashCode() {
        if (this.smallValues != null) {
            int h = 0;
            String[] keys = this.smallKeys;
            Tag<?>[] values = this.smallValues;
            int n = this.smallSize;
            for (int i = 0; i < n; i++) {
                String key = keys[i];
                Tag<?> value = values[i];
                h += (key == null ? 0 : key.hashCode()) ^ (value == null ? 0 : value.hashCode());
            }
            return h;
        }
        return this.map().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        // Strict class match preserves the Tag.equals contract: two compounds of different runtime
        // classes (e.g. the EMPTY anonymous subclass vs a plain CompoundTag) never compare equal.
        // Size short-circuit then beats the Map.equals subtree walk on every mismatched pair without
        // the per-entry recursion.
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompoundTag other = (CompoundTag) o;
        if (this.size() != other.size()) return false;

        // Equal-size, equal-class. Walk this compound's entries and probe the other side. Both
        // sides may be in either mode; the public get() handles both cases.
        if (this.smallValues != null) {
            String[] keys = this.smallKeys;
            Tag<?>[] values = this.smallValues;
            int n = this.smallSize;
            for (int i = 0; i < n; i++) {
                Tag<?> mine = values[i];
                Tag<?> theirs = other.get(keys[i]);
                if (!Objects.equals(mine, theirs))
                    return false;
            }
            return true;
        }
        return this.map().equals(other.getValue());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull String toString() {
        StringBuilder builder = new StringBuilder();
        this.appendTo(builder, 0);
        return builder.toString();
    }

    /**
     * Appends a depth- and width-truncated rendering of this compound to the given builder.
     *
     * @param builder the destination
     * @param depth   current nesting depth (0 at the root)
     */
    void appendTo(@NotNull StringBuilder builder, int depth) {
        if (depth >= TO_STRING_MAX_DEPTH) {
            builder.append("{...}");
            return;
        }

        int total = this.size();
        if (total == 0) {
            builder.append("{}");
            return;
        }

        builder.append('{');
        int i = 0;
        for (Map.Entry<String, Tag<?>> entry : this) {
            if (i >= TO_STRING_MAX_ENTRIES) {
                builder.append(", ... (").append(total - TO_STRING_MAX_ENTRIES).append(" more)");
                break;
            }
            if (i > 0) builder.append(", ");
            builder.append(entry.getKey()).append('=');
            appendChild(builder, entry.getValue(), depth + 1);
            i++;
        }
        builder.append('}');
    }

    /**
     * Renders a child tag, recursing into nested compounds and lists with depth tracking.
     */
    static void appendChild(@NotNull StringBuilder builder, Tag<?> tag, int depth) {
        if (tag instanceof CompoundTag compoundTag)
            compoundTag.appendTo(builder, depth);
        else if (tag instanceof ListTag<?> listTag)
            listTag.appendTo(builder, depth);
        else
            builder.append(tag);
    }

    // ------------------------------------------------------------------
    // Small-mode iterator
    // ------------------------------------------------------------------

    /**
     * Allocation-light iterator that walks the parallel arrays in insertion order without
     * promoting. Yields a fresh {@link Map.Entry} per element via {@link Map#entry(Object, Object)};
     * callers that only need to read keys + values pay no map-bucket overhead.
     */
    private final class SmallIterator implements Iterator<Map.Entry<String, Tag<?>>> {

        private int cursor = 0;

        @Override
        public boolean hasNext() {
            return this.cursor < CompoundTag.this.smallSize;
        }

        @Override
        public Map.Entry<String, Tag<?>> next() {
            if (this.cursor >= CompoundTag.this.smallSize)
                throw new NoSuchElementException();
            String key = CompoundTag.this.smallKeys[this.cursor];
            Tag<?> value = CompoundTag.this.smallValues[this.cursor];
            this.cursor++;
            return Map.entry(key, value);
        }
    }

}
