package lib.minecraft.nbt.tags.borrow;

import lib.minecraft.nbt.tags.CompoundTag;
import lib.minecraft.nbt.tags.Tag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Borrowed view over a {@link TapeKind#COMPOUND_HEADER}-bracketed tape range.
 *
 * <p>Behaves identically to {@link CompoundTag} from the consumer perspective - it IS a
 * {@code CompoundTag}, just one whose backing {@link Map} is a {@link TapeMapView} that decodes
 * entries lazily from the underlying {@link Tape}. Reads through the standard {@code Map} surface
 * ({@link #containsKey}, {@link #get}, {@link #size}, ...) consult the tape on a per-key basis;
 * the first mutation promotes the view to a {@link LinkedHashMap} and
 * every subsequent operation forwards there.</p>
 *
 * <p>Buffer-retention contract from {@code NbtFactory.borrowFromByteArray} applies: callers must
 * keep this tag reachable for the lifetime of any access into the retained input bytes.</p>
 *
 * @see CompoundTag
 * @see TapeMapView
 */
@ApiStatus.Experimental
public final class BorrowedCompoundTag extends CompoundTag {

    /**
     * Constructs a borrowed compound rooted at {@code tapeIndex} on {@code tape}.
     *
     * <p>Allocation in this constructor is just the supplier closure - the {@link TapeMapView}
     * itself is not built until the first {@link #getValue()} call (which any {@code Map} read
     * triggers).</p>
     *
     * @param tape the tape to navigate
     * @param tapeIndex tape index of the {@link TapeKind#COMPOUND_HEADER} entry
     */
    public BorrowedCompoundTag(@NotNull Tape tape, int tapeIndex) {
        super(viewSupplier(tape, tapeIndex));
    }

    private static @NotNull Supplier<Map<String, Tag<?>>> viewSupplier(@NotNull Tape tape, int tapeIndex) {
        return () -> new TapeMapView(tape, tapeIndex);
    }

}
