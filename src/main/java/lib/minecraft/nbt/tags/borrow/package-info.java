/**
 * Zero-allocation NBT navigation for read-heavy workloads.
 *
 * <p>The entry point {@link NbtFactory#borrowFromByteArray(byte[])
 * NbtFactory.borrowFromByteArray} parses the input bytes once into a flat tape - a {@link Tape}
 * composed of a packed {@code long[]} plus the retained {@code byte[]} backing buffer - and
 * returns a {@link BorrowedCompoundTag} navigator rooted at the parsed compound.</p>
 *
 * <h2>Tape representation</h2>
 *
 * <p>Each NBT tag is encoded as one or more entries in the tape's {@code long[]} - the high 8 bits
 * carry a {@link TapeKind} discriminant, the low 56 bits carry either an inline primitive value or
 * a byte offset into the retained buffer for strings, arrays, longs, and doubles. Containers
 * ({@code COMPOUND_HEADER} / {@code LIST_HEADER}) pack an approximate length and a tape index
 * pointing at their matching {@code *_END} marker, so skipping past a subtree is O(1) instead of
 * O(subtree size).</p>
 *
 * <h2>Lazy decode and zero-copy semantics</h2>
 *
 * <p>Each borrowed tag is a thin subclass of its materialize counterpart that holds a
 * {@code (Tape, int tapeIndex)} pair and decodes value bytes on demand.
 * {@link BorrowedCompoundTag} extends {@link CompoundTag} and is backed by a {@link TapeMapView}
 * that resolves keys via a per-call linear scan; {@link BorrowedListTag} extends {@link ListTag}
 * and is backed by a {@link TapeListView}. Primitive and array borrow types
 * ({@link BorrowedIntTag}, {@link BorrowedByteArrayTag}, etc.) override the standard typed
 * accessors ({@code intValue()}, {@code forEachByte()}, ...) to read direct from the tape buffer
 * without materializing the boxed wrapper or copying the array.</p>
 *
 * <p>The win comes from <b>skipping the decode of every field the caller never touches</b>: a
 * compound with thirty entries where the caller reads three pays decode cost for three, not
 * thirty. MUTF-8 key comparison via {@link MutfStringView#equalsString(String)} takes an ASCII
 * fast path that compares bytes against {@code char}s directly with no decode, so the linear key
 * scan inside {@link Tape#findChildTapeIndex(int, String)} also pays zero allocations on the
 * typical (ASCII-keyed) input.</p>
 *
 * <p>Strings still materialize through {@link MutfStringView#toString()} once the caller asks for
 * the decoded form, and primitive arrays still materialize through {@link RawList#toIntArray()}
 * and its byte / long siblings when the caller asks for an owned {@code int[]} / {@code long[]} /
 * {@code byte[]}.</p>
 *
 * <h2>Performance</h2>
 *
 * <p>The {@code BorrowBenchmarks} JMH suite measures {@code borrowDecodeAndAccessRoot} against
 * {@code materializingDecode}. Compound- and string-heavy fixtures land in the 2-3x range;
 * primitive-array-heavy fixtures land closer to 1.2-1.5x because the array bytes still byte-swap
 * into a fresh {@code int[]} / {@code long[]} on access. Workloads that read every field end up
 * roughly at parity with the materializing path - the borrow API is a win for selective access,
 * not an across-the-board speedup.</p>
 *
 * <h2>Buffer-retention contract</h2>
 *
 * <p>The returned {@link BorrowedCompoundTag} retains a strong reference to the (possibly
 * decompressed) input bytes through the underlying {@link Tape}. Pointer-kind tape elements
 * address bytes inside that retained array, so the array stays alive as long as any borrowed view
 * derived from this call is reachable. Callers must not assume the input array is eligible for
 * garbage collection just because the parse call has returned, and must not mutate the array
 * after passing it in - any write corrupts every pointer-kind tape element addressing it.</p>
 *
 * <h2>Escape hatch</h2>
 *
 * <p>Because every borrowed tag IS-A {@link Tag} of the matching kind, the consumer surface is
 * unified: pass a {@link BorrowedCompoundTag} anywhere a {@link CompoundTag} is accepted, and the
 * standard {@code Map} / {@code List} read methods work transparently. Two convenience methods
 * force the lazy tape view to be allocated and return the same tag as a plain
 * {@code CompoundTag} / {@code ListTag} reference: {@link BorrowedCompoundTag#materialize()} and
 * {@link BorrowedListTag#materialize()}. The {@code BorrowParityTest} pins the contract: the
 * materialized tree compares {@code equals} byte-for-byte to the result of
 * {@link NbtFactory#fromByteArray(byte[])} on the same input.</p>
 *
 * <h2>Stability</h2>
 *
 * <p>Every type in this package is annotated {@link Experimental &#64;ApiStatus.Experimental} -
 * the on-tape bit layout, the kind enum constants, and the public navigator surface may change
 * across minor releases until the borrow API graduates. {@link NbtInputTape} (the
 * {@link NbtInput} backend that builds tapes from raw bytes) lives in
 * {@code lib.minecraft.nbt.io.tape}; callers should reach it through
 * {@link NbtFactory#borrowFromByteArray(byte[])} rather than touching it directly.</p>
 *
 * @see NbtFactory#borrowFromByteArray(byte[])
 * @see NbtInputTape
 */
@ApiStatus.Experimental
package lib.minecraft.nbt.tags.borrow;

import lib.minecraft.nbt.NbtFactory;
import lib.minecraft.nbt.io.NbtInput;
import lib.minecraft.nbt.io.tape.NbtInputTape;
import lib.minecraft.nbt.tags.CompoundTag;
import lib.minecraft.nbt.tags.ListTag;
import lib.minecraft.nbt.tags.Tag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.Experimental;
