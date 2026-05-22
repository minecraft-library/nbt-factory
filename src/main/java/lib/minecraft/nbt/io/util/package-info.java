/**
 * Internal codec utilities shared across the NBT I/O backends.
 *
 * <p>Houses tiny growable primitive buffers ({@link lib.minecraft.nbt.io.util.ByteList}, {@link lib.minecraft.nbt.io.util.IntList}, {@link lib.minecraft.nbt.io.util.LongList})
 * used by the SNBT and JSON deserializers to read typed arrays without allocating per-element
 * boxed wrappers, the {@link lib.minecraft.nbt.io.util.NbtByteCodec} big-endian primitive codec and {@link lib.minecraft.nbt.io.util.NbtModifiedUtf8}
 * string decoder used by the buffer- and tape-backed parsers, and the {@link lib.minecraft.nbt.io.util.NbtKnownKeys}
 * zero-allocation canonical-key lookup table consulted on every compound-key read. Every type in
 * this package is {@link Internal @ApiStatus.Internal} - external callers must not depend on
 * it.</p>
 */
@ApiStatus.Internal
package lib.minecraft.nbt.io.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.Internal;
