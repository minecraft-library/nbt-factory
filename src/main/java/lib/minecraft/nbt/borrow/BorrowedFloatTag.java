package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.tags.TagType;
import lib.minecraft.nbt.tags.primitive.FloatTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Borrowed view over a {@link TapeKind#FLOAT_INLINE} tape entry. The IEEE-754 32-bit bit pattern
 * is stashed in the low 56 bits and rehydrated via {@link Float#intBitsToFloat(int)}.
 *
 * @see FloatTag
 */
@ApiStatus.Experimental
public final class BorrowedFloatTag implements BorrowedTag<Float> {

    private final @NotNull Tape tape;

    private final int tapeIndex;

    BorrowedFloatTag(@NotNull Tape tape, int tapeIndex) {
        this.tape = tape;
        this.tapeIndex = tapeIndex;
    }

    /**
     * Decodes the {@code float} value from the inline tape element.
     *
     * @return the float value
     */
    public float getFloatValue() {
        return Float.intBitsToFloat((int) TapeElement.unpackValue(this.tape.elementAt(this.tapeIndex)));
    }

    @Override
    public byte getId() {
        return TagType.FLOAT.getId();
    }

    @Override
    public @NotNull FloatTag materialize() {
        return new FloatTag(this.getFloatValue());
    }

}
