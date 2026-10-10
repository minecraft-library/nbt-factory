package lib.minecraft.nbt.tag;

import lib.minecraft.nbt.tag.borrow.BorrowedListTag;
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
import java.util.function.Supplier;

/**
 * {@link TagType#LIST} (ID 9) is used for storing an ordered list of {@link Tag Tags}.
 *
 * <p>The elements may differ in type, as a vanilla list's may. The list's type is read off its
 * elements when asked for, as vanilla reads it when writing: {@link TagType#END} for an empty list,
 * the type every element shares, or {@link TagType#COMPOUND} for a mixed list - the type the binary
 * form writes it under, each element then travelling wrapped in a compound under the empty key, see
 * {@link CompoundTag#isWrapper()}.</p>
 *
 * <p>Two lists are equal when they hold equal elements in the same order.</p>
 */
public class ListTag<E extends Tag<?>> extends Tag<List<E>> implements List<E>, Comparable<ListTag<E>> {

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
     * Constructs a list tag with a given {@code List<>} value.
     *
     * @param value the tag's {@code List<>} value
     */
    public ListTag(@NotNull List<E> value) {
        super(value);
    }

    /**
     * Constructs a list tag whose backing list is supplied lazily on first access. Used by
     * {@link BorrowedListTag}, which answers {@link #getListType()} and {@link #isMixed()} without
     * firing the supplier.
     *
     * @param supplier supplier invoked on first {@code getValue()}
     */
    protected ListTag(@NotNull Supplier<List<E>> supplier) {
        super(supplier);
    }

    /**
     * Returns the element a list holds for one read from the binary form - the tag a
     * {@link CompoundTag#isWrapper() wrapper} compound wraps, and any other tag as it is. Only a
     * reader unwraps: in memory a wrapper-shaped compound is a value of its own, which
     * {@link #wrapIfNeeded(byte, Tag)} wraps again on write so it reads back as itself.
     *
     * @param element the element as read
     * @return the element the list holds
     */
    public static @NotNull Tag<?> unwrap(@NotNull Tag<?> element) {
        return element instanceof CompoundTag compound && compound.isWrapper() ? compound.get("") : element;
    }

    /**
     * Returns the tag the binary form writes for one element of a list written under the given type.
     *
     * <p>Under {@link TagType#COMPOUND} every element travels as a compound, so any other element is
     * wrapped under the empty key, and so is a compound that is itself shaped like a wrapper, which
     * reading would otherwise take for one. Under any other type the element is written as it is.</p>
     *
     * @param listType the type the list is written under
     * @param element the element
     * @return the tag to write
     */
    public static @NotNull Tag<?> wrapIfNeeded(byte listType, @NotNull Tag<?> element) {
        if (listType != TagType.COMPOUND.getId())
            return element;

        if (element instanceof CompoundTag compound && !compound.isWrapper())
            return compound;

        CompoundTag wrapper = new CompoundTag(1);
        wrapper.put("", element);
        return wrapper;
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
     * Appends the specified element to the end of the list.
     *
     * @param element the element to be added
     * @return true, as the list always takes the element
     */
    @Override
    public boolean add(@NotNull E element) {
        return this.getValue().add(element);
    }

    /**
     * Inserts the specified tag at the specified position in this list.
     * Shifts the tag currently at that position and any subsequent tags to the right.
     *
     * @param index index at which the tag is to be inserted
     * @param element element to be inserted
     */
    @Override
    public void add(int index, E element) {
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
        this.getValue().clear();
    }

    @Override
    public int compareTo(@NotNull ListTag<E> o) {
        return Integer.compare(this.size(), o.size());
    }

    /**
     * Returns true if this list contains the tag, false otherwise.
     *
     * @param obj the tag to check for
     * @return true if this list contains the tag, false otherwise
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
     * @param collection the values to be checked for
     * @return true if this list contains all tags in the collection, false otherwise
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
        // Loosened to instanceof ListTag so a materialized list and a BorrowedListTag holding the
        // same elements compare equal across backends. The declared type of an empty list takes no
        // part, as it takes none in vanilla's equality.
        if (this == o) return true;
        if (!(o instanceof ListTag<?> listTag)) return false;

        if (this.size() != listTag.size()) return false;
        return this.getValue().equals(listTag.getValue());
    }

    @Override
    public void forEach(@NotNull Consumer<? super E> action) {
        for (E tag : this)
            action.accept(tag);
    }

    /**
     * Retrieves a tag value from its index in the list.
     *
     * @param index the index of the tag value to be retrieved
     * @return the tag value at the specified index
     */
    @Override
    public @NotNull E get(int index) {
        return this.getValue().get(index);
    }

    /**
     * Gets the NBT tag id of the list elements, read off the elements as vanilla reads it when
     * writing - {@link TagType#END} for an empty list, the id every element carries, and
     * {@link TagType#COMPOUND} for a mixed list, the id the binary form writes one under.
     */
    public byte getListType() {
        if (this.isEmpty())
            return TagType.END.getId();

        return this.isMixed() ? TagType.COMPOUND.getId() : this.getValue().getFirst().getId();
    }

    @Override
    public int hashCode() {
        return this.getValue().hashCode();
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

    /**
     * Gets whether the elements of this list differ in type.
     */
    public boolean isMixed() {
        List<E> values = this.getValue();

        for (int i = 1; i < values.size(); i++) {
            if (values.get(i).getId() != values.getFirst().getId())
                return true;
        }

        return false;
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
     * @param index the index of the tag to be removed
     * @return the removed tag
     */
    @Override
    public @NotNull E remove(int index) {
        return this.getValue().remove(index);
    }

    /**
     * Removes a given tag from the list. Returns true if removed successfully, false otherwise.
     *
     * @param obj the tag to be removed
     * @return true if the tag was removed successfully, false otherwise
     */
    @Override
    public boolean remove(Object obj) {
        return this.removeIf(tag -> Objects.equals((obj instanceof Tag<?>) ? tag : tag.getValue(), obj));
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
        StringBuilder builder = new StringBuilder();
        this.appendTo(builder, 0);
        return builder.toString();
    }


    /**
     * Appends a depth- and width-truncated rendering of this list to the given builder.
     *
     * @param builder the destination
     * @param depth current nesting depth (0 at the root)
     */
    void appendTo(@NotNull StringBuilder builder, int depth) {
        if (depth >= CompoundTag.TO_STRING_MAX_DEPTH) {
            builder.append("[...]");
            return;
        }

        List<E> list = this.getValue();
        if (list.isEmpty()) {
            builder.append("[]");
            return;
        }

        builder.append('[');
        int total = list.size();
        int rendered = Math.min(total, CompoundTag.TO_STRING_MAX_ENTRIES);
        for (int i = 0; i < rendered; i++) {
            if (i > 0) builder.append(", ");
            CompoundTag.appendChild(builder, list.get(i), depth + 1);
        }
        if (total > rendered)
            builder.append(", ... (").append(total - rendered).append(" more)");
        builder.append(']');
    }

}
