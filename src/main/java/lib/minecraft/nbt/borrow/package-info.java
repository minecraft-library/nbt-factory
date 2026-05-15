/**
 * Zero-allocation NBT navigation for read-heavy workloads.
 *
 * <p>Mirrors {@code simdnbt::borrow}
 * (<a href="https://git.matdoes.dev/mat/simdnbt/src/branch/master/simdnbt/src/borrow">borrow source</a>).
 * The entry point {@link lib.minecraft.nbt.NbtFactory#borrowFromByteArray(byte[])
 * NbtFactory.borrowFromByteArray} parses the input bytes once into a flat tape - a
 * {@link lib.minecraft.nbt.borrow.Tape Tape} composed of a packed {@code long[]} plus the retained
 * {@code byte[]} backing buffer - and returns a {@link lib.minecraft.nbt.borrow.BorrowedCompoundTag
 * BorrowedCompoundTag} navigator rooted at the parsed compound.</p>
 *
 * <h2>Tape representation</h2>
 *
 * <p>Each NBT tag is encoded as one or more entries in the tape's {@code long[]} - the high 8 bits
 * carry a {@link lib.minecraft.nbt.borrow.TapeKind TapeKind} discriminant, the low 56 bits carry
 * either an inline primitive value or a byte offset into the retained buffer for strings, arrays,
 * longs, and doubles. Containers ({@code COMPOUND_HEADER} / {@code LIST_HEADER}) pack an approximate
 * length and a tape index pointing at their matching {@code *_END} marker, so skipping past a
 * subtree is O(1) instead of O(subtree size).</p>
 *
 * <h2>Lazy decode and zero-copy semantics</h2>
 *
 * <p>Navigators are {@code (tape, tapeIndex)} pairs that decode on demand. Per-field allocation
 * still happens at navigation time - construction of a navigator object is two field stores, but
 * the work behind {@link lib.minecraft.nbt.borrow.MutfStringView#toString() MutfStringView.toString},
 * {@link lib.minecraft.nbt.borrow.RawList#toIntArray() RawList.toIntArray}, and the per-element
 * {@code Map.Entry} from {@link lib.minecraft.nbt.borrow.BorrowedCompoundTag#entries()
 * BorrowedCompoundTag.entries} is the same it would be on the materializing path.</p>
 *
 * <p>The win comes from <b>skipping the decode of every field the caller never touches</b>: a
 * compound with thirty entries where the caller reads three pays decode cost for three, not thirty.
 * MUTF-8 key comparison via {@link lib.minecraft.nbt.borrow.MutfStringView#equalsString(String)
 * MutfStringView.equalsString} takes an ASCII fast path that compares bytes against {@code char}s
 * directly with no decode, so the linear key scan inside
 * {@link lib.minecraft.nbt.borrow.Tape#findChildTapeIndex(int, String) findChildTapeIndex} also pays
 * zero allocations on the typical (ASCII-keyed) input.</p>
 *
 * <p>Strings still materialize through {@link lib.minecraft.nbt.borrow.MutfStringView#toString()
 * MutfStringView.toString} once the caller asks for the decoded form, primitive arrays still
 * materialize through {@link lib.minecraft.nbt.borrow.RawList#toIntArray() RawList.toIntArray} and
 * its byte / long siblings, and {@link lib.minecraft.nbt.borrow.BorrowedTag#materialize()
 * BorrowedTag.materialize} reconstructs the equivalent owned tag tree on demand. This is not a
 * full zero-copy parity with {@code simdnbt::borrow} - it is a lazy-decode parity, which is the
 * bound the JVM allows without going through {@code MemorySegment} or off-heap buffers.</p>
 *
 * <h2>Performance</h2>
 *
 * <p>The Phase C6 JMH benchmark ({@code BorrowVsMaterializeBenchmark}) measured <b>~2.26x</b> on
 * {@code complex_player.dat} when only a handful of fields are read on each pass.
 * Compound- and string-heavy fixtures land in the 2-3x range; primitive-array-heavy fixtures land
 * closer to 1.2-1.5x because the array bytes still byte-swap into a fresh {@code int[]} /
 * {@code long[]} on access. Workloads that read every field end up roughly at parity with the
 * materializing path - the borrow API is a win for selective access, not an across-the-board
 * speedup.</p>
 *
 * <h2>Buffer-retention contract</h2>
 *
 * <p>The returned {@link lib.minecraft.nbt.borrow.BorrowedCompoundTag BorrowedCompoundTag} retains
 * a strong reference to the (possibly decompressed) input bytes through the underlying
 * {@link lib.minecraft.nbt.borrow.Tape Tape}. Pointer-kind tape elements address bytes inside that
 * retained array, so the array stays alive as long as any borrowed view derived from this call is
 * reachable. Callers must not assume the input array is eligible for garbage collection just
 * because the parse call has returned, and must not mutate the array after passing it in - any
 * write corrupts every pointer-kind tape element addressing it.</p>
 *
 * <h2>Escape hatch</h2>
 *
 * <p>{@link lib.minecraft.nbt.borrow.BorrowedTag#materialize() BorrowedTag.materialize} returns
 * the equivalent owned {@link lib.minecraft.nbt.tags.collection.CompoundTag CompoundTag} (or
 * primitive / array / list tag) detached from the retained buffer. The {@code BorrowParityTest}
 * pins the contract: the materialized tree compares {@code equals} byte-for-byte to the result of
 * {@link lib.minecraft.nbt.NbtFactory#fromByteArray(byte[]) NbtFactory.fromByteArray} on the same
 * input. Use {@code materialize()} to escape the borrow scope; once the borrowed views and the
 * materialized tree are both reachable, the input bytes can fall out of reach (the materialized
 * tree carries no reference to them).</p>
 *
 * <h2>Stability</h2>
 *
 * <p>Every type in this package is annotated
 * {@link Experimental &#64;ApiStatus.Experimental} - the
 * on-tape bit layout, the kind enum constants, and the public navigator surface may change across
 * minor releases until the borrow API graduates. {@link lib.minecraft.nbt.borrow.TapeParser
 * TapeParser} is annotated {@link Internal &#64;ApiStatus.Internal}
 * - callers should reach the parser through
 * {@link lib.minecraft.nbt.NbtFactory#borrowFromByteArray(byte[]) NbtFactory.borrowFromByteArray}
 * rather than touching it directly.</p>
 *
 * @see lib.minecraft.nbt.NbtFactory#borrowFromByteArray(byte[])
 * @see <a href="https://git.matdoes.dev/mat/simdnbt/src/branch/master/simdnbt/src/borrow">simdnbt borrow source</a>
 */
@ApiStatus.Experimental
package lib.minecraft.nbt.borrow;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.ApiStatus.Internal;
