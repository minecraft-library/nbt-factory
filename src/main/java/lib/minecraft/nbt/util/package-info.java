/**
 * Shared utilities not coupled to a specific NBT I/O backend.
 *
 * <p>Houses general-purpose helpers consumed by both the materializing parser and the borrow tape
 * parser - for example {@link lib.minecraft.nbt.util.NbtKnownKeys NbtKnownKeys}, the zero-allocation
 * lookup table for canonical Minecraft NBT key strings. Members are wire-format agnostic; helpers
 * that decode raw bytes live in {@link lib.minecraft.nbt.io.util} instead.</p>
 *
 * <p>Every type in this package is {@link Internal @ApiStatus.Internal} - external callers must not
 * depend on it.</p>
 */
@ApiStatus.Internal
package lib.minecraft.nbt.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.Internal;
