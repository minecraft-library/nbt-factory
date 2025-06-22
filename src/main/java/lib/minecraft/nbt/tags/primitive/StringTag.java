package dev.sbs.minecraftapi.nbt.tags.primitive;

import dev.sbs.minecraftapi.nbt.tags.Tag;
import dev.sbs.minecraftapi.nbt.tags.TagType;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

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

    @Override
    public final @NotNull StringTag clone() {
        return new StringTag(this.getValue());
    }

    @Override
    public byte getId() {
        return TagType.STRING.getId();
    }

    @Override
    public final @NotNull String toString() {
        return this.getValue();
    }

}
