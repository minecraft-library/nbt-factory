package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.exception.NbtException;
import lib.minecraft.nbt.exception.NbtTypeException;
import lib.minecraft.nbt.io.util.NbtByteCodec;
import lib.minecraft.nbt.tags.Tag;
import lib.minecraft.nbt.tags.primitive.ByteTag;
import lib.minecraft.nbt.tags.primitive.DoubleTag;
import lib.minecraft.nbt.tags.primitive.FloatTag;
import lib.minecraft.nbt.tags.primitive.IntTag;
import lib.minecraft.nbt.tags.primitive.LongTag;
import lib.minecraft.nbt.tags.primitive.ShortTag;
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
 *
 * <p>Primitive kinds (byte / short / int / long / float / double) are decoded eagerly in this
 * dispatch and the standard {@code Tag.of(...)} factory cache is consulted - small values are
 * interned and never allocate, large values allocate a plain materialize tag. There is no
 * {@code BorrowedByteTag} / {@code BorrowedIntTag} / etc. wrapper for the primitive kinds; the
 * memory cost of a primitive borrow read collapses to a {@link Long} array element on the tape
 * plus, at most, a cache-miss boxing.</p>
 *
 * <p>String, array, compound, and list kinds keep dedicated borrow types because their value is
 * non-trivial to decode (MUTF-8, array copy, recursive map / list walk) and per-field laziness is
 * the actual borrow win for them.</p>
 */
@ApiStatus.Internal
public final class BorrowedTag {

    private BorrowedTag() {}

    /**
     * Polymorphic constructor: dispatches on the {@link TapeKind} at {@code valueIndex} and returns
     * the matching concrete tag typed as {@link Tag}.
     *
     * <p>For primitive kinds, decodes the tape element directly into a cached {@code Tag.of(...)}
     * instance - no closure, no per-tag wrapper. For string / array / compound / list kinds,
     * allocates a borrow-specific subclass that defers the expensive decode work until first
     * access through the standard {@link Tag} accessor surface.</p>
     *
     * @param tape the tape to navigate
     * @param valueIndex tape index of a value element (any kind except {@code KEY_PTR},
     *     {@code COMPOUND_END}, or {@code LIST_END})
     * @return the matching tag instance
     * @throws NbtException if the tape entry's kind is not a value kind
     */
    public static @NotNull Tag<?> fromTape(@NotNull Tape tape, int valueIndex) {
        long element = tape.elementAt(valueIndex);
        TapeKind kind = TapeElement.unpackKind(element);

        return switch (kind) {
            case BYTE_INLINE -> ByteTag.of((byte) TapeElement.unpackValue(element));
            case SHORT_INLINE -> ShortTag.of((short) TapeElement.unpackValue(element));
            case INT_INLINE -> IntTag.of((int) TapeElement.unpackValue(element));
            case FLOAT_INLINE -> new FloatTag(Float.intBitsToFloat((int) TapeElement.unpackValue(element)));
            case LONG_PTR -> {
                int offset = (int) TapeElement.unpackValue(element);
                yield LongTag.of(NbtByteCodec.getLong(tape.buffer(), offset));
            }
            case DOUBLE_PTR -> {
                int offset = (int) TapeElement.unpackValue(element);
                yield new DoubleTag(NbtByteCodec.getDouble(tape.buffer(), offset));
            }
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
