package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.exception.NbtException;
import lib.minecraft.nbt.exception.NbtTypeException;
import lib.minecraft.nbt.tags.Tag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Static factory for borrow-mode tag navigators.
 *
 * <p>Used to be the sealed root of a parallel borrow-mode hierarchy with its own {@code
 * materialize()} entry point. The hierarchy collapsed when every {@code Borrowed*Tag} type was
 * lifted to extend its materialize counterpart directly - {@link Tag} is now the single root for
 * both backends. What remains is the {@link #fromTape(Tape, int)} dispatch: given a tape index,
 * pick the right concrete subclass and return it as a plain {@link Tag}.</p>
 */
@ApiStatus.Internal
public final class BorrowedTag {

    private BorrowedTag() {}

    /**
     * Polymorphic constructor: dispatches on the {@link TapeKind} at {@code valueIndex} and returns
     * the matching concrete navigator typed as {@link Tag}.
     *
     * <p>Construction is cheap - just two field stores plus a supplier closure. The expensive work
     * (string decode, primitive byteswap, key-name comparison) is deferred to the first accessor
     * call.</p>
     *
     * @param tape the tape to navigate
     * @param valueIndex tape index of a value element (any kind except {@code KEY_PTR},
     *     {@code COMPOUND_END}, or {@code LIST_END})
     * @return a navigator over the tape entry
     * @throws NbtException if the tape entry's kind is not a value kind
     */
    public static @NotNull Tag<?> fromTape(@NotNull Tape tape, int valueIndex) {
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
            default -> throw new NbtTypeException(
                "Cannot construct BorrowedTag from kind %s at tape index %d", kind, valueIndex);
        };
    }

}
