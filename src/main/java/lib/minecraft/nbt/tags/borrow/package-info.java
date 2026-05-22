/**
 * Zero-allocation NBT navigation for read-heavy workloads.
 *
 * <p>Mirrors {@code simdnbt::borrow}
 * (<a href="https://git.matdoes.dev/mat/simdnbt/src/branch/master/simdnbt/src/borrow">borrow source</a>).
 * The entry point {@link lib.minecraft.nbt.NbtFactory#borrowFromByteArray(byte[])
 * NbtFactory.borrowFromByteArray} parses the input bytes once into a flat tape - a
 * {@link lib.minecraft.nbt.tags.borrow.Tape Tape} composed of a packed {@code long[]} plus the retained
 * {@code byte[]} backing buffer - and returns a {@link lib.minecraft.nbt.tags.borrow.BorrowedCompoundTag
 * BorrowedCompoundTag} navigator rooted at the parsed compound.</p>
 *
 * <h2>Tape representation</h2>
 *
 * <p>Each NBT tag is encoded as one or more entries in the tape's {@code long[]} - the high 8 bits
 * carry a {@link lib.minecraft.nbt.tags.borrow.TapeKind TapeKind} discriminant, the low 56 bits carry
 * either an inline primitive value or a byte offset into the retained buffer for strings, arrays,
 * longs, and doubles. Containers ({@code COMPOUND_HEADER} / {@code LIST_HEADER}) pack an approximate
 * length and a tape index pointing at their matching {@code *_END} marker, so skipping past a
 * subtree is O(1) instead of O(subtree size).</p>
 *
 * <h2>Lazy decode and zero-copy semantics</h2>
 *
 * <p>Each borrowed tag is a thin subclass of its materialize counterpart that holds a
 * {@code (Tape, int tapeIndex)} pair and decodes value bytes on demand. {@code BorrowedCompoundTag}
 * extends {@link lib.minecraft.nbt.tags.CompoundTag CompoundTag} and is backed by a
 * {@link lib.minecraft.nbt.tags.borrow.TapeMapView TapeMapView} that resolves keys via a per-call
 * linear scan; {@code BorrowedListTag} extends {@link lib.minecraft.nbt.tags.ListTag ListTag} and is
 * backed by a {@link lib.minecraft.nbt.tags.borrow.TapeListView TapeListView}. Primitive and array
 * borrow types ({@code BorrowedIntTag}, {@code BorrowedByteArrayTag}, etc.) override the
 * standard typed accessors ({@code intValue()}, {@code forEachByte()}, ...) to read direct from
 * the tape buffer without materializing the boxed wrapper or copying the array.</p>
 *
 * <p>The win comes from <b>skipping the decode of every field the caller never touches</b>: a
 * compound with thirty entries where the caller reads three pays decode cost for three, not thirty.
 * MUTF-8 key comparison via {@link lib.minecraft.nbt.tags.borrow.MutfStringView#equalsString(String)
 * MutfStringView.equalsString} takes an ASCII fast path that compares bytes against {@code char}s
 * directly with no decode, so the linear key scan inside
 * {@link lib.minecraft.nbt.tags.borrow.Tape#findChildTapeIndex(int, String) findChildTapeIndex} also pays
 * zero allocations on the typical (ASCII-keyed) input.</p>
 *
 * <p>Strings still materialize through {@link lib.minecraft.nbt.tags.borrow.MutfStringView#toString()
 * MutfStringView.toString} once the caller asks for the decoded form, and primitive arrays still
 * materialize through {@link lib.minecraft.nbt.tags.borrow.RawList#toIntArray() RawList.toIntArray}
 * and its byte / long siblings when the caller asks for an owned {@code int[]} / {@code long[]} /
 * {@code byte[]}. This is not full zero-copy parity with {@code simdnbt::borrow} - it is a
 * lazy-decode parity, which is the bound the JVM allows without going through
 * {@code MemorySegment} or off-heap buffers.</p>
 *
 * <h2>Performance</h2>
 *
 * <p>The {@code BorrowBenchmarks} JMH suite measures {@code borrowDecodeAndAccessRoot} against
 * {@code materializingDecode} on the simdnbt corpus. Compound- and string-heavy fixtures land in
 * the 2-3x range; primitive-array-heavy fixtures land closer to 1.2-1.5x because the array bytes
 * still byte-swap into a fresh {@code int[]} / {@code long[]} on access. Workloads that read
 * every field end up roughly at parity with the materializing path - the borrow API is a win for
 * selective access, not an across-the-board speedup.</p>
 *
 * <h2>Buffer-retention contract</h2>
 *
 * <p>The returned {@link lib.minecraft.nbt.tags.borrow.BorrowedCompoundTag BorrowedCompoundTag} retains
 * a strong reference to the (possibly decompressed) input bytes through the underlying
 * {@link lib.minecraft.nbt.tags.borrow.Tape Tape}. Pointer-kind tape elements address bytes inside that
 * retained array, so the array stays alive as long as any borrowed view derived from this call is
 * reachable. Callers must not assume the input array is eligible for garbage collection just
 * because the parse call has returned, and must not mutate the array after passing it in - any
 * write corrupts every pointer-kind tape element addressing it.</p>
 *
 * <h2>Escape hatch</h2>
 *
 * <p>Because every borrowed tag IS-A {@link lib.minecraft.nbt.tags.Tag Tag} of the matching kind,
 * the consumer surface is unified: pass a {@code BorrowedCompoundTag} anywhere a
 * {@link lib.minecraft.nbt.tags.CompoundTag CompoundTag} is accepted, and the standard
 * {@code Map} / {@code List} read methods work transparently. Two convenience methods force the
 * lazy tape view to be allocated and return the same tag as a plain
 * {@code CompoundTag} / {@code ListTag} reference:
 * {@link lib.minecraft.nbt.tags.borrow.BorrowedCompoundTag#materialize()
 * BorrowedCompoundTag.materialize} and
 * {@link lib.minecraft.nbt.tags.borrow.BorrowedListTag#materialize() BorrowedListTag.materialize}.
 * The {@code BorrowParityTest} pins the contract: the materialized tree compares {@code equals}
 * byte-for-byte to the result of
 * {@link lib.minecraft.nbt.NbtFactory#fromByteArray(byte[]) NbtFactory.fromByteArray} on the same
 * input.</p>
 *
 * <h2>Stability</h2>
 *
 * <p>Every type in this package is annotated
 * {@link Experimental &#64;ApiStatus.Experimental} - the on-tape bit layout, the kind enum
 * constants, and the public navigator surface may change across minor releases until the borrow
 * API graduates. {@link lib.minecraft.nbt.io.tape.TapeInput TapeInput} (the NbtInput backend that
 * builds tapes from raw bytes) lives in {@code lib.minecraft.nbt.io.tape}; callers should reach
 * it through {@link lib.minecraft.nbt.NbtFactory#borrowFromByteArray(byte[])
 * NbtFactory.borrowFromByteArray} rather than touching it directly.</p>
 *
 * @see lib.minecraft.nbt.NbtFactory#borrowFromByteArray(byte[])
 * @see lib.minecraft.nbt.io.tape.TapeInput
 * @see <a href="https://git.matdoes.dev/mat/simdnbt/src/branch/master/simdnbt/src/borrow">simdnbt borrow source</a>
 */
@ApiStatus.Experimental
package lib.minecraft.nbt.tags.borrow;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.Experimental;
