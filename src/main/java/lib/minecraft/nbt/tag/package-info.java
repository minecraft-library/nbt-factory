/**
 * Public NBT data model - the {@link Tag} hierarchy that {@link NbtFactory} reads and writes.
 *
 * <p>Every value in an NBT payload is a {@link Tag} of one of the thirteen kinds enumerated by
 * {@link TagType}. The hierarchy splits along three axes:</p>
 *
 * <ul>
 *   <li><b>Numeric primitives.</b> {@link ByteTag}, {@link ShortTag}, {@link IntTag},
 *       {@link LongTag}, {@link FloatTag}, {@link DoubleTag} - all extend {@link NumericalTag}
 *       so consumers can pull the value as any primitive width without down-casting first. The
 *       small integer kinds expose {@code of(...)} factories that share cached instances across
 *       the typical wire-format value range, so a parser reading a million {@code Count} tags
 *       allocates zero {@code ByteTag} wrappers.</li>
 *   <li><b>String and array primitives.</b> {@link StringTag} carries a modified-UTF-8 payload;
 *       {@link ByteArrayTag}, {@link IntArrayTag}, {@link LongArrayTag} carry typed primitive
 *       arrays. The array kinds expose a {@code forEachXxx} primitive consumer entry point so
 *       borrowed views can read direct from a retained buffer without boxing.</li>
 *   <li><b>Containers.</b> {@link CompoundTag} is an order-preserving map of name to {@link Tag};
 *       {@link ListTag} is a homogeneous list with a wire element id pinned at construction.
 *       Both are mutable through the standard {@link Map} / {@link List} surfaces.</li>
 * </ul>
 *
 * <p>{@link EndTag} (id {@code 0}) is the wire-level marker that closes a compound; it does not
 * appear as a value inside a parsed tree, only on the wire.</p>
 *
 * <h2>Lazy construction</h2>
 *
 * <p>Every concrete tag exposes a {@link Supplier}-taking constructor in addition to the
 * value-taking one. The supplier form is invoked once on the first {@link Tag#getValue()} call
 * and cached - the {@code lib.minecraft.nbt.tag.borrow} navigators use this to defer decode of
 * each field until the consumer asks for it. The supplier form is package-stable; downstream code
 * may rely on it for its own lazy backings.</p>
 *
 * <h2>Mutability</h2>
 *
 * <p>{@link CompoundTag} and {@link ListTag} are mutable. The primitive and array tags are
 * effectively immutable through their typed accessors; {@link Tag#setValue(Object)} still works
 * for the rare caller that wants to mutate in place. The {@code EMPTY} sentinels on the leaf tag
 * types override {@code setValue} to throw, so they remain safe to share as a no-allocation
 * default.</p>
 */
package lib.minecraft.nbt.tag;

import lib.minecraft.nbt.NbtFactory;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
