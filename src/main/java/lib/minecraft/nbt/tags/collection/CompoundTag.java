package dev.sbs.minecraftapi.nbt.tags.collection;

import dev.sbs.minecraftapi.nbt.tags.Tag;
import dev.sbs.minecraftapi.nbt.tags.TagType;
import dev.sbs.minecraftapi.nbt.tags.array.ByteArrayTag;
import dev.sbs.minecraftapi.nbt.tags.array.IntArrayTag;
import dev.sbs.minecraftapi.nbt.tags.array.LongArrayTag;
import dev.sbs.minecraftapi.nbt.tags.primitive.ByteTag;
import dev.sbs.minecraftapi.nbt.tags.primitive.DoubleTag;
import dev.sbs.minecraftapi.nbt.tags.primitive.FloatTag;
import dev.sbs.minecraftapi.nbt.tags.primitive.IntTag;
import dev.sbs.minecraftapi.nbt.tags.primitive.LongTag;
import dev.sbs.minecraftapi.nbt.tags.primitive.ShortTag;
import dev.sbs.minecraftapi.nbt.tags.primitive.StringTag;
import dev.sbs.api.stream.pair.Pair;
import dev.sbs.api.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;

/**
 * {@link TagType#COMPOUND} (ID 10) is used for storing an ordered list of key-{@link Tag value} pairs.
 */
@SuppressWarnings("unchecked")
public class CompoundTag extends Tag<Map<String, Tag<?>>> implements Map<String, Tag<?>>, Iterable<Map.Entry<String, Tag<?>>> {

    public static final @NotNull CompoundTag EMPTY = new CompoundTag() {
        @Override
        public void requireModifiable() {
            throw new UnsupportedOperationException("This nbt tag is not modifiable.");
        }
    };

    /**
     * Constructs an empty, unnamed compound tag.
     */
    public CompoundTag() {
        this(new LinkedHashMap<>());
    }

    /**
     * Constructs a compound tag with a given name and {@code Map<>} value.
     *
     * @param value the tag's {@code Map<>} value.
     */
    public CompoundTag(@NotNull Map<String, Tag<?>> value) {
        super(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        this.getValue().clear();
    }

    @Override
    @SuppressWarnings("all")
    public final @NotNull CompoundTag clone() {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putAll(this.getValue());
        return compoundTag;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsKey(@NotNull Object key) {
        return this.getValue().containsKey(key);
    }

    public boolean containsListOf(@NotNull String key, byte of) {
        return this.containsType(key, TagType.LIST) && this.getListTag(key).getListType() == of;
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
    @SuppressWarnings("")
    public boolean containsType(@NotNull String key, byte typeId) {
        if (!this.containsKey(key))
            return false;

        return Objects.requireNonNull(this.get(key)).getId() == typeId;
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
        List<String> entries = StringUtil.toList(StringUtil.split(path, "\\."));
        CompoundTag current = this;

        for (String entry : entries) {
            Tag<?> childTag = current.get(entry);

            if (childTag == null)
                return false;

            if (!(childTag instanceof CompoundTag))
                return true;

            current = (CompoundTag) childTag;
        }

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsValue(@NotNull Object value) {
        return this.values()
            .stream()
            .anyMatch(tagValue -> Objects.equals(((value instanceof Tag<?>) ? tagValue : tagValue.getValue()), value));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull Set<Map.Entry<String, Tag<?>>> entrySet() {
        return this.getValue().entrySet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void forEach(@NotNull Consumer<? super Entry<String, Tag<?>>> action) {
        this.getValue().forEach((key, value) -> action.accept(Pair.of(key, value)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable Tag<?> get(@Nullable Object key) {
        return this.getOrDefault(key, null);
    }

    public <T extends Tag<?>> @NotNull T getOrDefault(@NotNull String key, @NotNull T defaultValue) {
        return this.containsKey(key) ? (T) this.getValue().get(key) : defaultValue;
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
        T value = defaultValue;

        if (this.containsPath(path)) {
            List<String> entries = StringUtil.toList(StringUtil.split(path, "\\."));
            CompoundTag compoundTag = this.getMap(entries.subList(0, entries.size() - 1), false);
            value = compoundTag.getTag(entries.get(entries.size() - 1));
        }

        return value;
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
     * {@inheritDoc}
     */
    @Override
    public boolean isEmpty() {
        return this.getValue().isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull Iterator<Map.Entry<String, Tag<?>>> iterator() {
        return this.getValue().entrySet().iterator();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull Set<String> keySet() {
        return this.getValue().keySet();
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
        return this.getValue().put(key, value);
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

    public @Nullable ByteArrayTag put(@NotNull String key, @NotNull Byte[] value) {
        return this.putTag(key, new ByteArrayTag(value));
    }

    public @Nullable StringTag put(@NotNull String key, @NotNull String value) {
        return this.putTag(key, new StringTag(value));
    }

    public <T extends Tag<?>> @Nullable ListTag<T> put(@NotNull String key, @NotNull ListTag<T> value) {
        return this.putTag(key, new ListTag<>(value));
    }

    public @Nullable CompoundTag put(@NotNull String key, @NotNull CompoundTag value) {
        return this.putTag(key, new CompoundTag(value));
    }

    public @Nullable IntArrayTag put(@NotNull String key, @NotNull Integer[] value) {
        return this.putTag(key, new IntArrayTag(value));
    }

    public @Nullable LongArrayTag put(@NotNull String key, @NotNull Long[] value) {
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
        List<String> entries = StringUtil.toList(StringUtil.split(path, "\\."));
        CompoundTag map = this.getMap(entries.subList(0, entries.size() - 1), true);
        map.put(entries.get(entries.size() - 1), value);
        // TODO: Save tag data
        return this;
    }

    public <T extends Tag<?>> @Nullable T putTag(@NotNull String key, @NotNull T value) {
        return (T) this.put(key, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable Tag<?> remove(Object key) {
        this.requireModifiable();
        return this.getValue().remove(key);
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
        List<String> entries = StringUtil.toList(StringUtil.split(path, "\\."));
        CompoundTag currentTag = this;

        for (int i = 0; i < entries.size(); i++) {
            String entry = entries.get(i);

            if (i == entries.size() - 1)
                Objects.requireNonNull(currentTag).remove(entry);
            else
                currentTag = Objects.requireNonNull(currentTag).getTag(entry);
        }

        // TODO: Save tag data
        return Objects.requireNonNull(currentTag);
    }

    public void requireModifiable() { }

    /**
     * {@inheritDoc}
     */
    public int size() {
        return this.getValue().size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Spliterator<Map.Entry<String, Tag<?>>> spliterator() {
        return this.getValue().entrySet().spliterator();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull Collection<Tag<?>> values() {
        return this.getValue().values();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull String toString() {
        return this.getValue().toString();
    }

}
