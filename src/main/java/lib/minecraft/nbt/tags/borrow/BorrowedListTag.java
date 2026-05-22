package lib.minecraft.nbt.tags.borrow;

import lib.minecraft.nbt.tags.ListTag;
import lib.minecraft.nbt.tags.Tag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Borrowed view over a {@link TapeKind#LIST_HEADER}-bracketed tape range.
 *
 * <p>Behaves identically to {@link ListTag} from the consumer perspective - it IS a
 * {@code ListTag<Tag<?>>}, just one whose backing {@link List} is a {@link TapeListView} that
 * decodes elements lazily from the underlying {@link Tape}. Reads through the standard
 * {@code List} surface ({@link #get(int)}, {@link #size}, {@link #iterator}, ...) consult the
 * tape on a per-index basis; the first mutation promotes the view to an {@link ArrayList} and
 * every subsequent operation forwards there.</p>
 *
 * <p>The packed list header carries the wire {@code elementId} (NBT tag id of the list's
 * elements) which is read directly from the header without forcing the supplier, so callers can
 * inspect {@link #getListType()} without paying for view allocation.</p>
 *
 * @see ListTag
 * @see TapeListView
 */
@ApiStatus.Experimental
public final class BorrowedListTag extends ListTag<Tag<?>> {

    /**
     * Constructs a borrowed list rooted at {@code tapeIndex} on {@code tape}. Pre-seeds the
     * element id from the tape header so {@link #getListType()} works before the supplier fires.
     *
     * @param tape the tape to navigate
     * @param tapeIndex tape index of the {@link TapeKind#LIST_HEADER} entry
     */
    public BorrowedListTag(@NotNull Tape tape, int tapeIndex) {
        super(TapeElement.unpackListElementId(tape.elementAt(tapeIndex)), viewSupplier(tape, tapeIndex));
    }

    private static @NotNull Supplier<List<Tag<?>>> viewSupplier(@NotNull Tape tape, int tapeIndex) {
        return () -> new TapeListView(tape, tapeIndex);
    }

}
