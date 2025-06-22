package dev.sbs.minecraftapi.nbt.tags.array;

import dev.sbs.minecraftapi.nbt.tags.Tag;
import dev.sbs.api.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * An abstract superclass of all {@link Tag Tags} representing numeric array values that can be converted to the primitive types.
 *
 * @param <T> the {@code Number} type this NBT tag represents.
 */
public abstract class ArrayTag<T extends Number> extends Tag<T[]> implements Iterable<T> {

    public static final Pattern NUMBER_PATTERN = Pattern.compile("[-0-9]+");

    protected ArrayTag(@NotNull T[] value) {
        super(value);
    }

    /**
     * Appends the specified element(s) to the end of the array tag.
     *
     * @param elements element(s) to be added.
     */
    @SafeVarargs
    public final void add(@NotNull T... elements) {
        this.insert(this.length(), elements);
    }

    /**
     * Removes all the elements from this array tag. The array tag will be empty after this call returns.
     */
    public final void clear() {
        this.requireModifiable();
        this.setValue(ArrayUtil.removeAll(this.getValue()));
    }

    @Override
    public abstract @NotNull ArrayTag<T> clone();

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ArrayTag<?> arrayTag = (ArrayTag<?>) o;
        return ArrayUtil.isEquals(this.getValue(), arrayTag.getValue());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void forEach(@NotNull Consumer<? super T> action) {
        Arrays.asList(this.getValue()).forEach(action);
    }

    /**
     * Returns the element at the specified position in this array tag.
     *
     * @param index index of the element to return.
     * @return the element at the specified position in this array tag.
     */
    public final @NotNull T get(int index) {
        return this.getValue()[index];
    }

    @Override
    public final int hashCode() {
        return Arrays.hashCode(this.getValue());
    }

    /**
     * Inserts the specified element(s) at the specified position in this array tag.
     * Shifts the element(s) currently at that position and any subsequent elements to the right.
     *
     * @param index    index at which the element(s) are to be inserted.
     * @param elements element(s) to be inserted.
     */
    @SafeVarargs
    public final void insert(int index, @NotNull T... elements) {
        this.requireModifiable();
        this.setValue(ArrayUtil.insert(index, this.getValue(), elements));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final @NotNull Iterator<T> iterator() {
        return Arrays.asList(this.getValue()).iterator();
    }

    /**
     * Get the number of elements in this array tag.
     */
    public final int length() {
        return this.getValue().length;
    }

    /**
     * Removes the element at the specified position in this array tag.
     * Shifts any subsequent elements to the left. Returns the element that was removed from the array tag.
     *
     * @param index the index of the element to be removed.
     * @return the element previously at the specified position.
     */
    public final @NotNull T remove(int index) {
        this.requireModifiable();
        T previous = this.getValue()[index];
        this.setValue(ArrayUtil.remove(this.getValue(), index));
        return previous;
    }

    protected void requireModifiable() { }

    /**
     * Replaces the element at the specified position in this array tag with the specified element.
     *
     * @param index   index of the element to replace.
     * @param element element to be stored at the specified position.
     * @return the element previously at the specified position.
     */
    public final @NotNull T set(int index, @NotNull T element) {
        this.requireModifiable();
        return this.getValue()[index] = element;
    }

    public final void setValue(@NotNull T @NotNull [] value) {
        this.requireModifiable();
        super.setValue(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final @NotNull Spliterator<T> spliterator() {
        return Arrays.asList(this.getValue()).spliterator();
    }

    @Override
    public final @NotNull String toString() {
        return ArrayUtil.toString(this.getValue());
    }

}
