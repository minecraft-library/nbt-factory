/**
 * Internal codec utilities shared across the NBT I/O backends.
 *
 * <p>Houses tiny growable primitive buffers ({@link lib.minecraft.nbt.io.util.ByteList ByteList},
 * {@link lib.minecraft.nbt.io.util.IntList IntList},
 * {@link lib.minecraft.nbt.io.util.LongList LongList}) used by the SNBT and JSON deserializers
 * to read typed arrays without allocating per-element boxed wrappers. Every type in this package
 * is {@link org.jetbrains.annotations.ApiStatus.Internal @ApiStatus.Internal} - external callers
 * must not depend on it.</p>
 */
@ApiStatus.Internal
package lib.minecraft.nbt.io.util;

import org.jetbrains.annotations.ApiStatus;
