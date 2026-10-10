package lib.minecraft.nbt.tag.borrow;

import lib.minecraft.nbt.tag.ListTag;
import lib.minecraft.nbt.tag.Tag;
import lib.minecraft.nbt.tag.TagType;
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
 * <p>The packed list header carries the {@code elementId} (NBT tag id of the list's elements as
 * the list holds them, or {@link TapeElement#MIXED_LIST_ELEMENT_ID} when they differ) which is read
 * directly from the header without forcing the supplier, so callers can inspect
 * {@link #getListType()} and {@link #isMixed()} without paying for view allocation.</p>
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
        this(TapeElement.unpackListElementId(tape.elementAt(tapeIndex)), tape, tapeIndex);
    }

    private BorrowedListTag(byte elementId, @NotNull Tape tape, int tapeIndex) {
        super(
            elementId == TapeElement.MIXED_LIST_ELEMENT_ID ? TagType.COMPOUND.getId() : elementId,
            elementId == TapeElement.MIXED_LIST_ELEMENT_ID,
            viewSupplier(tape, tapeIndex)
        );
    }

    private static @NotNull Supplier<List<Tag<?>>> viewSupplier(@NotNull Tape tape, int tapeIndex) {
        return () -> new TapeListView(tape, tapeIndex);
    }

}
