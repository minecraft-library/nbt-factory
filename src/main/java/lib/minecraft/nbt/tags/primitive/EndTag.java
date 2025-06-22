package dev.sbs.minecraftapi.nbt.tags.primitive;

import dev.sbs.minecraftapi.nbt.tags.Tag;
import dev.sbs.minecraftapi.nbt.tags.TagType;
import dev.sbs.minecraftapi.nbt.tags.collection.CompoundTag;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link TagType#END} (ID 0) represents the end of a {@link CompoundTag}.
 */
@Getter
@SuppressWarnings("all")
public final class EndTag extends Tag<Void> {

    public static final @NotNull EndTag INSTANCE = new EndTag();

    private EndTag() {
        super(null);
    }

    @Override
    @SuppressWarnings("all")
    public @NotNull Tag<Void> clone() {
        return INSTANCE;
    }

    @Override
    public byte getId() {
        return TagType.END.getId();
    }

    @Override
    public @Nullable Void getValue() {
        return null;
    }

    @Override
    public void setValue(@NotNull Void value) {

    }

    @Override
    public @NotNull String toString() {
        return "\"end\"";
    }

}
