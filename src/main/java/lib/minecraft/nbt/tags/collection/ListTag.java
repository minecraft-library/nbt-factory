package dev.sbs.minecraftapi.nbt.tags.collection;

import dev.sbs.minecraftapi.nbt.tags.Tag;
import dev.sbs.minecraftapi.nbt.tags.TagType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * {@link TagType#LIST} (ID 9) is used for storing an ordered list of {@link Tag Tags}.
 */
public class ListTag<E extends Tag<?>> extends Tag<List<E>> implements List<E>, Comparable<ListTag<E>> {

    private byte elementId;

    /**
     * Constructs an empty, unnamed list tag backed by an {@link ArrayList}.
     */
    public ListTag() {
        this(new ArrayList<>());
    }

    /**
     * Constructs an empty, unnamed list tag backed by an {@link ArrayList} pre-sized to the given capacity.
     *
     * @param initialCapacity initial capacity for the backing list
     */
    public ListTag(int initialCapacity) {
        this(new ArrayList<>(initialCapacity));
    }

    /**
     * Constructs an empty, unnamed list tag pre-sized to {@code initialCapacity} with its element
     * type fixed to {@code elementId} before the first {@link #add} call.
     *
     * <p>Used by the NBT deserialization hot path, where the element type is already known from
     * the list-type byte in the wire format. Pre-seeding lets {@code add} skip the {@code isEmpty}
     * probe on every subsequent element - one fewer method call per list entry during bulk
     * deserialization.</p>
     *
     * @param elementId       the NBT tag id all future entries must carry
     * @param initialCapacity initial capacity for the backing list
     */
    public ListTag(byte elementId, int initialCapacity) {
        this(new ArrayList<>(initialCapacity));
        this.elementId = elementId;
    }

    /**
     * Constructs a list tag with a given name and {@code List<>} value.
     *
     * @param value the tag's {@code List<>} value.
     */
    public ListTag(@NotNull List<E> value) {
        super(value);
    }

    @Override
    public final byte getId() {
        return TagType.LIST.getId();
    }

    @Override
    @SuppressWarnings("all")
    public final @NotNull ListTag<E> clone() {
        return new ListTag<>(new ArrayList<>(this.getValue()));
    }

    /**
     * Appends the specified element to the end of the list. Returns true if added successfully.
     *
     * @param element the element to be added.
     * @return true if added successfully.
     */
    @Override
    public boolean add(@NotNull E element) {
        byte id = element.getId();

        // elementId == 0 (END) is used as the sentinel for "list has no fixed type yet". END is
        // never a valid list element type, so this is unambiguous. Skipping the isEmpty() probe
        // eliminates one ArrayList method call per add during bulk deserialization.
        if (this.elementId == 0)
            this.elementId = id;
        else if (id != this.elementId)
            return false;

        return this.getValue().add(element);
    }

    /**
     * Inserts the specified tag at the specified position in this list.
     * Shifts the tag currently at that position and any subsequent tags to the right.
     *
     * @param index   index at which the tag is to be inserted.
     * @param element element to be inserted.
     */
    @Override
    public void add(int index, E element) {
        byte id = element.getId();

        if (this.elementId == 0)
            this.elementId = id;
        else if (id != this.elementId)
            return;

        this.getValue().add(index, element);
    }

    @Override
    public boolean addAll(Collection<? extends E> collection) {
        boolean changed = false;

        for (E element : collection)
            changed |= this.add(element);

        return changed;
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> collection) {
        int previousSize = this.size();
        int start = -1;

        for (E element : collection)
            this.add(index + ++start, element);

        return this.size() > previousSize;
    }

    /**
     * Removes all tags from the list. The list will be empty after this call returns.
     */
    @Override
    public void clear() {
        this.elementId = 0;
        this.getValue().clear();
    }

    @Override
    public int compareTo(@NotNull ListTag<E> o) {
        return Integer.compare(this.size(), o.size());
    }

    /**
     * Returns true if this list contains the tag, false otherwise.
     *
     * @param obj the tag to check for.
     * @return true if this list contains the tag, false otherwise.
     */
    @Override
    public boolean contains(Object obj) {
        boolean compareTag = obj instanceof Tag<?>;

        for (E tag : this.getValue()) {
            if (Objects.equals(compareTag ? tag : tag.getValue(), obj))
                return true;
        }

        return false;
    }

    /**
     * Returns true if this list contains all tags in the collection, false otherwise.
     *
     * @param collection the values to be checked for.
     * @return true if this list contains all tags in the collection, false otherwise.
     */
    @Override
    public boolean containsAll(@NotNull Collection<?> collection) {
        if (this.isEmpty() || collection.isEmpty())
            return false;

        boolean isTags = collection.iterator().next() instanceof Tag<?>;

        for (E tag : this.getValue()) {
            if (!collection.contains(isTags ? tag : tag.getValue()))
                return false;
        }

        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ListTag<?> listTag = (ListTag<?>) o;

        return this.getListType() == listTag.getListType()
            && Objects.equals(this.getValue(), listTag.getValue());
    }

    @Override
    public void forEach(@NotNull Consumer<? super E> action) {
        for (E tag : this)
            action.accept(tag);
    }

    /**
     * Retrieves a tag value from its index in the list.
     *
     * @param index the index of the tag value to be retrieved.
     * @return the tag value at the specified index.
     */
    @Override
    public @NotNull E get(int index) {
        return this.getValue().get(index);
    }

    /**
     * Gets the NBT tag id of the list elements.
     */
    public byte getListType() {
        return this.elementId;
    }

    @Override
    public int hashCode() {
        return 31 * this.getValue().hashCode() + this.getListType();
    }

    @Override
    public int indexOf(@Nullable Object obj) {
        boolean compareTag = obj instanceof Tag<?>;
        List<E> values = this.getValue();
        int size = values.size();

        for (int i = 0; i < size; i++) {
            E tag = values.get(i);

            if (Objects.equals(compareTag ? tag : tag.getValue(), obj))
                return i;
        }

        return -1;
    }

    /**
     * Gets if this list tag is empty.
     */
    @Override
    public boolean isEmpty() {
        return this.getValue().isEmpty();
    }

    @Override
    public @NotNull Iterator<E> iterator() {
        return this.getValue().iterator();
    }

    @Override
    public int lastIndexOf(@Nullable Object obj) {
        boolean compareTag = obj instanceof Tag<?>;
        List<E> values = this.getValue();

        for (int i = values.size() - 1; i >= 0; i--) {
            E tag = values.get(i);

            if (Objects.equals(compareTag ? tag : tag.getValue(), obj))
                return i;
        }

        return -1;
    }

    @Override
    public @NotNull ListIterator<E> listIterator() {
        return this.listIterator(0);
    }

    @Override
    public @NotNull ListIterator<E> listIterator(int index) {
        return this.getValue().listIterator();
    }

    /**
     * Removes a tag from the list based on the tag's index. Returns the removed tag.
     *
     * @param index the index of the tag to be removed.
     * @return the removed tag.
     */
    @Override
    public @NotNull E remove(int index) {
        E previous = this.getValue().remove(index);

        if (this.isEmpty())
            this.elementId = 0;

        return previous;
    }

    /**
     * Removes a given tag from the list. Returns true if removed successfully, false otherwise.
     *
     * @param obj the tag to be removed.
     * @return true if the tag was removed successfully, false otherwise.
     */
    @Override
    public boolean remove(Object obj) {
        boolean result = this.removeIf(tag -> Objects.equals((obj instanceof Tag<?>) ? tag : tag.getValue(), obj));

        if (this.isEmpty())
            this.elementId = 0;

        return result;
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> collection) {
        int resultSize = this.size() - collection.size();
        this.removeIf(tag -> collection.contains(tag) || collection.contains(tag.getValue()));
        return this.size() == resultSize;
    }

    @Override
    public boolean removeIf(@NotNull Predicate<? super E> filter) {
        return this.getValue().removeIf(filter);
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> collection) {
        int resultSize = this.size() - collection.size();
        this.removeIf(tag -> !(collection.contains(tag) || collection.contains(tag.getValue())));
        return this.size() == resultSize;
    }

    @Override
    public E set(int index, @NotNull E element) {
        return this.getValue().set(index, element);
    }

    /**
     * Gets the number of elements in this list tag.
     */
    @Override
    public int size() {
        return this.getValue().size();
    }

    @Override
    public @NotNull Spliterator<E> spliterator() {
        return this.getValue().spliterator();
    }

    @Override
    public @NotNull ListTag<E> subList(int fromIndex, int toIndex) {
        return new ListTag<>(this.getValue().subList(fromIndex, toIndex));
    }

    @Override
    public @NotNull Object @NotNull [] toArray() {
        return this.getValue().toArray();
    }

    @Override
    public <T> @NotNull T @NotNull [] toArray(@NotNull T @NotNull [] array) {
        return this.getValue().toArray(array);
    }

    @Override
    public @NotNull String toString() {
        return this.getValue().toString();
    }

}
