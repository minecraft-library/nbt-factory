package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.tags.TagType;
import lib.minecraft.nbt.tags.primitive.StringTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Borrowed view over a {@link TapeKind#STRING_PTR} tape entry. The tape element addresses a
 * 2-byte big-endian length prefix followed by {@code length} bytes of modified UTF-8.
 *
 * <p>Backed by a {@link MutfStringView} that defers modified-UTF-8 decode until first access.
 * {@link #getValue()} and {@link #materialize()} both route through {@link MutfStringView#toString()}
 * so the decoded {@link String} is shared across calls.</p>
 *
 * @see StringTag
 * @see MutfStringView
 */
@ApiStatus.Experimental
public final class BorrowedStringTag implements BorrowedTag<String> {

    private final @NotNull MutfStringView view;

    BorrowedStringTag(@NotNull Tape tape, int tapeIndex) {
        int tagOffset = (int) TapeElement.unpackValue(tape.elementAt(tapeIndex));
        this.view = MutfStringView.fromTagOffset(tape.buffer(), tagOffset);
    }

    /**
     * Returns the decoded {@link String} value. The first call decodes via the backing
     * {@link MutfStringView} and caches the result; subsequent calls return the same reference.
     *
     * @return the decoded string
     */
    public @NotNull String getValue() {
        return this.view.toString();
    }

    /**
     * Returns the underlying {@link MutfStringView}. Exposed so callers that only need to compare
     * or hash the string can avoid the decode on the ASCII fast path.
     *
     * @return the backing view
     */
    public @NotNull MutfStringView view() {
        return this.view;
    }

    @Override
    public byte getId() {
        return TagType.STRING.getId();
    }

    @Override
    public @NotNull StringTag materialize() {
        return new StringTag(this.view.toString());
    }

}
