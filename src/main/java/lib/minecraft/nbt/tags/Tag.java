package lib.minecraft.nbt.tags;

import lib.minecraft.nbt.exception.NbtMaxDepthException;
import lib.minecraft.nbt.io.NbtInput;
import lib.minecraft.nbt.io.NbtOutput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * <p>Interface for all NBT tags.</p>
 *
 * <p>All serializing and deserializing methods data track the nesting levels to prevent
 * circular references or malicious data which could, when deserialized, result in thousands
 * of instances causing a denial of service.</p>
 *
 * <p>These {@link NbtInput} and {@link NbtOutput} methods have a parameter for the
 * nesting depth they have currently traversed. A maximum value of
 * {@code 512} means that only the object itself, but no nested objects may be
 * processed. If an instance is nested deeper than {@code 512}, an
 * {@link NbtMaxDepthException} will be thrown. An
 * {@code IllegalArgumentException} is thrown for a negative nesting depth.</p>
 *
 * <p>The held value can be provided eagerly via {@link #Tag(Object)} or deferred via
 * {@link #Tag(Supplier)} - the supplier runs at most once on first {@link #getValue()} and the
 * result is cached. The borrowed-tag construction path uses the supplier form so its value is
 * decoded lazily from the underlying {@code Tape} without retaining a reference to the tape
 * once the decode runs.</p>
 *
 * @param <T> The type of the contained value
 * */
public abstract class Tag<T> implements Cloneable {

    private @Nullable T value;

    private @Nullable Supplier<T> supplier;

    protected Tag(@NotNull T value) {
        this.value = value;
    }

    protected Tag(@NotNull Supplier<T> supplier) {
        this.supplier = supplier;
    }

    /**
     * Creates a clone of this Tag.
     * */
    public abstract @NotNull Tag<T> clone();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Tag<?> tag)) return false;
        if (this.getId() != tag.getId()) return false;

        return this.getValue().equals(tag.getValue());
    }

    /**
     * Returns the value held by this tag.
     *
     * <p>If this tag was constructed with a {@link Supplier} (the borrowed-tag construction
     * path), the supplier is invoked once on first access and the result is cached; every
     * subsequent call returns the cached value with no supplier dispatch.</p>
     */
    public @NotNull T getValue() {
        Supplier<T> s = this.supplier;

        if (s != null) {
            this.value = s.get();
            this.supplier = null;
        }

        return this.value;
    }

    /**
     * Replaces the value held by this tag. Discards any pending supplier so subsequent reads
     * return the new value directly.
     */
    public void setValue(@NotNull T value) {
        this.value = value;
        this.supplier = null;
    }

    /**
     * Gets the unique ID for this NBT tag type.
     * <br><br>
     * 0 to 12 (inclusive) are reserved.
     */
    public abstract byte getId();

    @Override
    public int hashCode() {
        return this.getValue().hashCode();
    }

    @Override
    public abstract @NotNull String toString();

}
