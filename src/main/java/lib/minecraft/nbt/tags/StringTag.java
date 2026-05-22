package lib.minecraft.nbt.tags;

import lib.minecraft.nbt.tags.Tag;
import lib.minecraft.nbt.tags.borrow.BorrowedStringTag;
import lib.minecraft.nbt.tags.TagType;
import dev.simplified.util.StringUtil;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * {@link TagType#STRING} (ID 8) is used for storing a UTF-8 encoded {@code String}.
 */
@Getter
public class StringTag extends Tag<String> {

    public static final @NotNull StringTag EMPTY = new StringTag() {
        @Override
        public void setValue(@NotNull String value) {
            throw new UnsupportedOperationException("This nbt tag is not modifiable.");
        }
    };

    /**
     * Constructs a string tag with an empty value.
     */
    public StringTag() {
        super("");
    }

    /**
     * Constructs a string tag with the given value.
     */
    public StringTag(@NotNull String value) {
        super(value);
    }

    /**
     * Constructs a string tag whose value is supplied lazily on first access. Used by
     * {@link BorrowedStringTag}.
     */
    public StringTag(@NotNull Supplier<String> supplier) {
        super(supplier);
    }

    @Override
    public final @NotNull StringTag clone() {
        return new StringTag(this.getValue());
    }

    @Override
    public byte getId() {
        return TagType.STRING.getId();
    }

    public boolean isEmpty() {
        return StringUtil.isEmpty(this.getValue());
    }

    public boolean notEmpty() {
        return !this.isEmpty();
    }

    @Override
    public final @NotNull String toString() {
        return this.getValue();
    }

}
