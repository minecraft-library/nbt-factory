package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.tags.TagType;
import lib.minecraft.nbt.tags.primitive.StringTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Borrowed view over a {@link TapeKind#STRING_PTR} tape entry. The tape element addresses a
 * 2-byte big-endian length prefix followed by {@code length} bytes of modified UTF-8.
 *
 * <p>Decoding is lazy and cached: the first call to {@link #getValue()} or {@link #materialize()}
 * runs {@link lib.minecraft.nbt.io.NbtModifiedUtf8#decode(byte[], int, int) NbtModifiedUtf8.decode}
 * and stashes the result; subsequent calls return the same {@link String} reference. Phase C4
 * replaces this single-shot decode with a {@code MutfStringView} backed by the same byte range -
 * the swap is transparent to {@link #materialize()} and source-compatible with {@link #getValue()}.</p>
 *
 * @see StringTag
 */
@ApiStatus.Experimental
public final class BorrowedStringTag implements BorrowedTag<String> {

    private final @NotNull Tape tape;

    private final int tapeIndex;

    /**
     * Cached decoded form. Single-thread by convention - the borrow API does not promise
     * thread-safe traversal, so a plain field beats the volatile/CAS overhead. Stays null until
     * the first {@link #getValue()} call.
     */
    private @Nullable String cached;

    BorrowedStringTag(@NotNull Tape tape, int tapeIndex) {
        this.tape = tape;
        this.tapeIndex = tapeIndex;
    }

    /**
     * Returns the decoded {@link String} value, decoding on first call and caching the result.
     *
     * @return the decoded string
     */
    public @NotNull String getValue() {
        String cached = this.cached;

        if (cached != null)
            return cached;

        int offset = (int) TapeElement.unpackValue(this.tape.elementAt(this.tapeIndex));
        String decoded = BorrowedTagSupport.decodeUtf8At(this.tape.buffer(), offset);
        this.cached = decoded;
        return decoded;
    }

    @Override
    public byte getId() {
        return TagType.STRING.getId();
    }

    @Override
    public @NotNull StringTag materialize() {
        return new StringTag(this.getValue());
    }

}
