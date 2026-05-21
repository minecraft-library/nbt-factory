package lib.minecraft.nbt.tags;

import org.jetbrains.annotations.NotNull;

/**
 * Sealed root of the NBT tag hierarchy.
 *
 * <p>Every concrete tag class implements one of the 14 leaf sub-interfaces below - the 13 wire
 * types defined by the Minecraft NBT format plus the synthetic {@link BooleanTag}, which encodes
 * as a {@code TAG_Byte} on the wire but carries the type at the API surface.</p>
 *
 * <p>Two backing strategies satisfy every leaf interface: the materializing implementations in
 * {@code lib.minecraft.nbt.tags.materialize} hold their value in a primitive field, and the
 * borrow implementations in {@code lib.minecraft.nbt.borrow} navigate a tape without allocating
 * until a mutator forces materialization. Both share the same public contract, so consumer code
 * works against the interface and never has to branch on backend.</p>
 *
 * @see TagType
 */
public sealed interface Tag permits
    NumericalTag, BooleanTag,
    StringTag, EndTag,
    ByteArrayTag, IntArrayTag, LongArrayTag,
    CompoundTag, ListTag {

    /**
     * The single-byte NBT wire id for this tag's type.
     */
    byte getId();

    /**
     * The {@link TagType} enum constant matching this tag's wire id.
     */
    @NotNull TagType getType();

    /**
     * Deep copy of this tag.
     */
    @NotNull Tag deepClone();

}
