/**
 * Borrow-mode NBT API - zero-copy parsing built on a flat tape of packed {@code long}s plus a
 * retained {@code byte[]} backing buffer.
 *
 * <p>Mirrors the architecture described in
 * <a href="https://git.matdoes.dev/mat/simdnbt/src/branch/master/simdnbt/src/borrow/tape.rs">simdnbt's
 * {@code borrow/tape.rs}</a>. Each NBT tag is encoded as one or more entries in
 * {@link lib.minecraft.nbt.borrow.Tape#elements} - the high 8 bits carry a
 * {@link lib.minecraft.nbt.borrow.TapeKind} discriminant, the low 56 bits carry either an inline
 * primitive value or a byte offset into the retained buffer for strings, arrays, longs, and
 * doubles. Containers ({@code COMPOUND_HEADER} / {@code LIST_HEADER}) pack an approximate length
 * and a tape index pointing at their matching {@code *_END} marker, allowing O(1) skip-past-subtree
 * traversal.</p>
 *
 * <p>This package is the foundation for a lazy-decoding parallel API to the materializing
 * {@link lib.minecraft.nbt.tags.collection.CompoundTag CompoundTag} surface. Strings and primitive
 * arrays are not eagerly materialized - tape elements hold offsets into the underlying byte buffer
 * and the C3+ borrowed tag types decode on demand. The C1 entry point
 * {@link lib.minecraft.nbt.borrow.Tape#encode(lib.minecraft.nbt.tags.collection.CompoundTag) Tape.encode(CompoundTag)}
 * is testability scaffolding; C2 replaces its body with a streaming binary parser that builds a
 * tape directly from a {@code byte[]} without ever materializing a {@code CompoundTag}.</p>
 *
 * <p>Every type in this package is annotated {@link org.jetbrains.annotations.ApiStatus.Experimental
 * &#64;ApiStatus.Experimental} - the on-tape bit layout, the kind enum constants, and the public
 * factory shape may change before the borrow API stabilizes alongside the public
 * {@code NbtFactory.borrowFromByteArray} entry point landing in C5.</p>
 *
 * @see lib.minecraft.nbt.NbtFactory
 * @see <a href="https://git.matdoes.dev/mat/simdnbt/src/branch/master/simdnbt/src/borrow/tape.rs">simdnbt borrow/tape.rs</a>
 */
@ApiStatus.Experimental
package lib.minecraft.nbt.borrow;

import org.jetbrains.annotations.ApiStatus;
