package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.exception.NbtException;
import lib.minecraft.nbt.tags.Tag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Sealed root of the borrow-mode tag hierarchy - a lazy-decoding navigator over a
 * {@link Tape}.
 *
 * <p>Every concrete subtype is backed by a {@code (Tape tape, int tapeIndex)} pair. Reads are
 * lazy: primitive accessors decode on demand from {@link Tape#buffer()} via
 * {@link lib.minecraft.nbt.io.NbtByteCodec}, strings decode through
 * {@link lib.minecraft.nbt.io.NbtModifiedUtf8} (Phase C4 will replace that with the
 * zero-copy {@code MutfStringView}), and arrays expose either a {@link RawList} view or a
 * materialized primitive array on demand.</p>
 *
 * <p>{@link #materialize()} produces the equivalent owned {@link Tag} - the
 * {@code BorrowParityTest} pins this against the existing
 * {@link lib.minecraft.nbt.NbtFactory#fromByteArray(byte[]) NbtFactory.fromByteArray} path on every
 * fixture.</p>
 *
 * @param <T> the materialized value type ({@code Byte}, {@code Short}, ..., {@code String},
 *     {@code byte[]}, {@code int[]}, {@code long[]}, or a {@code Map}/{@code List} of {@code Tag}).
 */
@ApiStatus.Experimental
public sealed interface BorrowedTag<T> permits
    BorrowedByteTag, BorrowedShortTag, BorrowedIntTag,
    BorrowedLongTag, BorrowedFloatTag, BorrowedDoubleTag,
    BorrowedStringTag,
    BorrowedByteArrayTag, BorrowedIntArrayTag, BorrowedLongArrayTag,
    BorrowedListTag, BorrowedCompoundTag {

    /**
     * Returns the materializing-API tag-id byte. Identical to
     * {@link lib.minecraft.nbt.tags.TagType#getId() TagType.getId()} for the equivalent owned tag,
     * so callers that already know how to dispatch on {@link Tag#getId()} can reuse the same
     * dispatch table for borrowed tags.
     *
     * @return the NBT wire tag-id byte
     */
    byte getId();

    /**
     * Materializes this borrow into the equivalent owned {@link Tag}.
     *
     * <p>Output must compare {@code equals} to the same value reached through the materializing
     * path - {@code BorrowParityTest} pins this contract on every fixture.</p>
     *
     * @return a freshly allocated owned tag
     */
    @NotNull Tag<T> materialize();

    /**
     * Polymorphic constructor: dispatches on the {@link TapeKind} at {@code valueIndex} and returns
     * the matching concrete navigator.
     *
     * <p>Construction is cheap - just two field stores. The expensive work (string decode,
     * primitive byteswap, key-name comparison) is deferred to the first accessor call.</p>
     *
     * @param tape the tape to navigate
     * @param valueIndex tape index of a value element (any kind except {@code KEY_PTR},
     *     {@code COMPOUND_END}, or {@code LIST_END})
     * @return a navigator over the tape entry
     * @throws NbtException if the tape entry's kind is not a value kind
     */
    static @NotNull BorrowedTag<?> fromTape(@NotNull Tape tape, int valueIndex) {
        long element = tape.elementAt(valueIndex);
        TapeKind kind = TapeElement.unpackKind(element);

        return switch (kind) {
            case BYTE_INLINE -> new BorrowedByteTag(tape, valueIndex);
            case SHORT_INLINE -> new BorrowedShortTag(tape, valueIndex);
            case INT_INLINE -> new BorrowedIntTag(tape, valueIndex);
            case FLOAT_INLINE -> new BorrowedFloatTag(tape, valueIndex);
            case LONG_PTR -> new BorrowedLongTag(tape, valueIndex);
            case DOUBLE_PTR -> new BorrowedDoubleTag(tape, valueIndex);
            case STRING_PTR -> new BorrowedStringTag(tape, valueIndex);
            case BYTE_ARRAY_PTR -> new BorrowedByteArrayTag(tape, valueIndex);
            case INT_ARRAY_PTR -> new BorrowedIntArrayTag(tape, valueIndex);
            case LONG_ARRAY_PTR -> new BorrowedLongArrayTag(tape, valueIndex);
            case COMPOUND_HEADER -> new BorrowedCompoundTag(tape, valueIndex);
            case LIST_HEADER -> new BorrowedListTag(tape, valueIndex);
            default -> throw new NbtException(
                "Cannot construct BorrowedTag from kind %s at tape index %d", kind, valueIndex);
        };
    }

}
