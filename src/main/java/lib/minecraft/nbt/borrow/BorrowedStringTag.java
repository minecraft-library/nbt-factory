package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.tags.primitive.StringTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Borrowed view over a {@link TapeKind#STRING_PTR} tape entry. The tape element addresses a
 * 2-byte big-endian length prefix followed by {@code length} bytes of modified UTF-8.
 *
 * <p>Backed by a {@link MutfStringView} that defers modified-UTF-8 decode until first access.
 * {@link #getValue()} routes through {@link MutfStringView#toString()} so the decoded
 * {@link String} is shared across calls.</p>
 *
 * @see StringTag
 * @see MutfStringView
 */
@ApiStatus.Experimental
public final class BorrowedStringTag extends StringTag {

    private final @NotNull MutfStringView view;

    BorrowedStringTag(@NotNull Tape tape, int tapeIndex) {
        this(MutfStringView.fromTagOffset(tape.buffer(),
            (int) TapeElement.unpackValue(tape.elementAt(tapeIndex))));
    }

    private BorrowedStringTag(@NotNull MutfStringView view) {
        super(view::toString);
        this.view = view;
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

}
