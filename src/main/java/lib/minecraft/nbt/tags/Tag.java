package dev.sbs.minecraftapi.nbt.tags;

import dev.sbs.minecraftapi.nbt.exception.NbtMaxDepthException;
import dev.sbs.minecraftapi.nbt.io.NbtInput;
import dev.sbs.minecraftapi.nbt.io.NbtOutput;
import dev.sbs.api.builder.EqualsBuilder;
import dev.sbs.api.builder.HashCodeBuilder;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

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
 * @param <T> The type of the contained value.
 * */
@Getter
@Setter
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class Tag<T> implements Cloneable {

    private @NotNull T value;

    /**
     * Creates a clone of this Tag.
     * */
    public abstract @NotNull Tag<T> clone();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Tag<?> tag = (Tag<?>) o;

        return new EqualsBuilder()
            .append(this.getValue(), tag.getValue())
            .build();
    }

    /**
     * Gets the unique ID for this NBT tag type.
     * <br><br>
     * 0 to 12 (inclusive) are reserved.
     */
    public abstract byte getId();

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getValue())
            .build();
    }

    @Override
    public abstract @NotNull String toString();

}
