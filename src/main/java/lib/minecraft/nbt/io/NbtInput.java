package lib.minecraft.nbt.io;

import com.google.gson.stream.JsonToken;
import lib.minecraft.nbt.exception.NbtMaxDepthException;
import lib.minecraft.nbt.io.json.NbtJsonDeserializer;
import lib.minecraft.nbt.tags.Tag;
import lib.minecraft.nbt.tags.array.ByteArrayTag;
import lib.minecraft.nbt.tags.array.IntArrayTag;
import lib.minecraft.nbt.tags.array.LongArrayTag;
import lib.minecraft.nbt.tags.collection.CompoundTag;
import lib.minecraft.nbt.tags.collection.ListTag;
import lib.minecraft.nbt.tags.primitive.ByteTag;
import lib.minecraft.nbt.tags.primitive.DoubleTag;
import lib.minecraft.nbt.tags.primitive.FloatTag;
import lib.minecraft.nbt.tags.primitive.IntTag;
import lib.minecraft.nbt.tags.primitive.LongTag;
import lib.minecraft.nbt.tags.primitive.ShortTag;
import lib.minecraft.nbt.tags.primitive.StringTag;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Arrays;

/**
 * Reader-side contract shared by every NBT backend in this module - the binary byte-array
 * backend ({@code NbtInputBuffer}), the streaming binary backend ({@code NbtInputStream}), and
 * the two text-based backends ({@code SnbtDeserializer},
 * {@link NbtJsonDeserializer}).
 *
 * <p>Minecraft's canonical wire format is framed, big-endian binary NBT: every value is prefixed
 * by a 1-byte type id, compounds carry a stream of {@code (type, name, value)} entries terminated
 * by a {@code TAG_End} (id 0), and lists carry a 1-byte element type plus a big-endian length
 * followed by that many elements of the declared type. See the
 * <a href="https://minecraft.wiki/w/NBT_format">Minecraft Wiki NBT format</a> for the full
 * specification. This interface splits the read path into two layers so every backend only has
 * to implement what is native to its input source:</p>
 *
 * <ul>
 *   <li><b>Primitive reads</b> - {@code readByte}, {@code readShort}, ..., {@code readDouble},
 *       {@code readUTF}, {@code readByteArray}, {@code readIntArray}, {@code readLongArray}
 *       are backend-specific. The byte-array and stream backends decode the big-endian bytes
 *       directly; the SNBT backend parses numeric literals with type suffixes; the JSON backend
 *       infers types from {@link JsonToken JsonToken} peeks.</li>
 *   <li><b>Structural reads</b> - {@link #readTag(byte, int)}, {@link #readListTag(int)}, and
 *       {@link #readCompoundTag(int)} ship with default implementations encoded against the
 *       binary wire layout so binary backends can accept them unchanged. Text backends override
 *       the structural methods because they have no inbound type byte - SNBT uses character
 *       lookahead, JSON uses {@code JsonToken} peek.</li>
 * </ul>
 *
 * <p>{@link #readTag(byte, int)} is the type-dispatched reader used when the element type is
 * already known from the framing (a list element type, a compound entry type, or a caller-
 * supplied id). Each branch constructs the concrete {@code Tag} subclass in place with the
 * primitive value, avoiding an intermediate boxing step on the hot deserialization path.</p>
 *
 * <p><b>Depth tracking.</b> {@code readListTag} and {@code readCompoundTag} increment
 * {@code depth} on entry and throw {@link NbtMaxDepthException} at depth {@code >= 512}. This
 * prevents adversarial inputs from producing stack-overflow-sized trees during deserialization;
 * the same guard fires on every backend, including the two text backends that override the
 * structural methods.</p>
 *
 * @see NbtOutput
 * @see <a href="https://minecraft.wiki/w/NBT_format">Minecraft Wiki - NBT format</a>
 */
public interface NbtInput {

    /**
     * Dispatches a tag read by id directly to the matching primitive {@code readXxx} method.
     *
     * <p>Inlined - no intermediate {@code readXxxTag} wrapper hop. The tag instance is constructed
     * once with the primitive value, avoiding an extra getter call.</p>
     */
    default @NotNull Tag<?> readTag(byte id, int maxDepth) throws IOException {
        return switch (id) {
            case 1 -> ByteTag.of(this.readByte());
            case 2 -> ShortTag.of(this.readShort());
            case 3 -> IntTag.of(this.readInt());
            case 4 -> LongTag.of(this.readLong());
            case 5 -> new FloatTag(this.readFloat());
            case 6 -> new DoubleTag(this.readDouble());
            case 7 -> new ByteArrayTag(this.readByteArray());
            case 8 -> new StringTag(this.readUTF());
            case 9 -> this.readListTag(maxDepth);
            case 10 -> this.readCompoundTag(maxDepth);
            case 11 -> new IntArrayTag(this.readIntArray());
            case 12 -> new LongArrayTag(this.readLongArray());
            default -> throw new UnsupportedOperationException("Tag with id " + id + " is not supported.");
        };
    }

    boolean readBoolean() throws IOException;

    byte readByte() throws IOException;

    short readShort() throws IOException;

    int readInt() throws IOException;

    long readLong() throws IOException;

    float readFloat() throws IOException;

    double readDouble() throws IOException;

    @NotNull String readUTF() throws IOException;

    byte @NotNull [] readByteArray() throws IOException;

    int @NotNull [] readIntArray() throws IOException;

    long @NotNull [] readLongArray() throws IOException;

    default @NotNull ListTag<?> readListTag() throws IOException {
        return this.readListTag(0);
    }

    /**
     * Reads an NBT {@code TAG_List} payload: element type byte, big-endian length, then that many
     * elements of the given type.
     *
     * <p>Binary NBT backends ({@code NbtInputBuffer}, {@code NbtInputStream}) share this
     * implementation. SNBT and any other text-based backend overrides with format-specific parsing.</p>
     *
     * <p>Implemented iteratively via {@link #readContainerIterative(byte, int)} so adversarially
     * deep input throws {@link NbtMaxDepthException} instead of crashing the JVM with a
     * {@code StackOverflowError} on the parsing thread.</p>
     */
    default @NotNull ListTag<?> readListTag(int depth) throws IOException {
        return (ListTag<?>) this.readContainerIterative((byte) 9, depth);
    }

    default @NotNull CompoundTag readCompoundTag() throws IOException {
        return this.readCompoundTag(0);
    }

    /**
     * Reads an NBT {@code TAG_Compound} payload: a sequence of {@code (type, name, value)} entries
     * terminated by a {@code TAG_End} (id 0).
     *
     * <p>Binary NBT backends share this implementation. SNBT and other text-based backends override
     * with format-specific parsing.</p>
     *
     * <p>Implemented iteratively via {@link #readContainerIterative(byte, int)} so adversarially
     * deep input throws {@link NbtMaxDepthException} instead of crashing the JVM with a
     * {@code StackOverflowError} on the parsing thread.</p>
     */
    default @NotNull CompoundTag readCompoundTag(int depth) throws IOException {
        return (CompoundTag) this.readContainerIterative((byte) 10, depth);
    }

    /**
     * Iterative container parser shared by {@link #readCompoundTag(int)} and
     * {@link #readListTag(int)}. Walks the tree with an explicit work-stack of container frames -
     * {@link CompoundTag} entries and {@link ListTag} entries push a new frame when the wire format
     * opens a nested container, leaf tags are read inline via {@link #readTag(byte, int)}. Mirrors
     * simdnbt's parsing-stack design (see {@code borrow/compound.rs}) so deeply nested adversarial
     * input trips {@link NbtMaxDepthException} instead of recursing through the JVM call stack.
     *
     * <p>The stack is allocated at 16 frames and grown by doubling up to a 512-frame ceiling that
     * matches the legacy recursive depth gate - a {@code depth} parameter of zero plus 511 levels
     * of nesting parses cleanly, the 512th nested container throws. Real-world payloads never
     * outgrow the initial 16 slots so a typical decode pays for one small allocation total.
     * Output is byte-identical to the prior recursive implementation for every input the recursive
     * path handled.</p>
     *
     * @param rootKind starting container type id - {@code 10} for {@code TAG_Compound},
     *                 {@code 9} for {@code TAG_List}
     * @param depth    starting depth (matches the legacy {@code depth} parameter)
     * @return the populated root container
     * @throws IOException           on backend read failure
     * @throws NbtMaxDepthException  if nesting would exceed 512 levels
     */
    private @NotNull Tag<?> readContainerIterative(byte rootKind, int depth) throws IOException {
        // Parallel-array work-stack, allocated small (16) and doubled on overflow up to the legacy
        // 512 depth cap. Real-world payloads (auctions.bin ~depth 8, level.dat ~depth 7,
        // simple_player.dat ~depth 5) all fit in the initial 16-slot stack so a typical decode pays
        // for one ~256-byte allocation across the four arrays - much cheaper than a fixed 512-slot
        // upfront alloc on the hot path. Indexing is "sp points at the topmost live frame", with
        // sp == -1 meaning the stack is empty (we have just popped the root).
        Object[] containers = new Object[16];
        byte[] kinds = new byte[16];          // 9 = LIST, 10 = COMPOUND
        byte[] listElementIds = new byte[16]; // valid only for LIST frames
        int[] listRemaining = new int[16];    // valid only for LIST frames

        // Push the root frame. Mirrors the legacy `if (++depth >= 512) throw` semantic: a starting
        // depth of 511 already disallows the root push.
        int newDepth = depth + 1;
        if (newDepth >= 512)
            throw new NbtMaxDepthException();

        int sp = 0;
        kinds[0] = rootKind;
        Tag<?> rootTag;

        if (rootKind == 10) {
            CompoundTag compound = new CompoundTag();
            containers[0] = compound;
            rootTag = compound;
        } else {
            byte listType = this.readByte();
            int length = Math.max(0, this.readInt());
            // Pre-seed elementId so ListTag.add skips the "first element" probe on every entry.
            ListTag<Tag<?>> listTag = new ListTag<>(listType, length);
            containers[0] = listTag;
            listElementIds[0] = listType;
            listRemaining[0] = length;
            rootTag = listTag;
        }

        // Walk until the work-stack drains. Each iteration consumes one entry from the top frame's
        // wire stream - either a leaf (read inline + attach) or a nested container (push + loop).
        while (sp >= 0) {
            byte kind = kinds[sp];

            if (kind == 10) { // COMPOUND
                CompoundTag compound = (CompoundTag) containers[sp];

                // readByte() & 0xFF is the unsigned-byte form. Avoids making readUnsignedByte
                // abstract on this interface, which would force SnbtDeserializer (whose readByte
                // parses text) to provide a meaningless implementation.
                int id = this.readByte() & 0xFF;

                if (id == 0) { // TAG_End
                    sp--;
                    continue;
                }

                String key = this.readUTF();

                if (id == 10 || id == 9) {
                    // Need to grow before write-through. Capacity check mirrors the recursive
                    // depth cap: parent frame at sp has effective depth sp + depth + 1, so the
                    // child push's effective depth is sp + depth + 2, rejected at >= 512.
                    int childDepth = sp + depth + 2;

                    if (childDepth >= 512)
                        throw new NbtMaxDepthException();

                    int newSp = sp + 1;

                    if (newSp >= containers.length) {
                        int grown = Math.min(containers.length << 1, 512);
                        containers = Arrays.copyOf(containers, grown);
                        kinds = Arrays.copyOf(kinds, grown);
                        listElementIds = Arrays.copyOf(listElementIds, grown);
                        listRemaining = Arrays.copyOf(listRemaining, grown);
                    }

                    Tag<?> child;
                    kinds[newSp] = (byte) id;

                    if (id == 10) {
                        CompoundTag c = new CompoundTag();
                        containers[newSp] = c;
                        child = c;
                    } else {
                        byte listType = this.readByte();
                        int length = Math.max(0, this.readInt());
                        ListTag<Tag<?>> listTag = new ListTag<>(listType, length);
                        containers[newSp] = listTag;
                        listElementIds[newSp] = listType;
                        listRemaining[newSp] = length;
                        child = listTag;
                    }

                    compound.put(key, child);
                    sp = newSp;
                    continue;
                }

                compound.put(key, this.readTag((byte) id, sp + depth + 1));
                continue;
            }

            // LIST
            int remaining = listRemaining[sp];

            if (remaining == 0) {
                sp--;
                continue;
            }

            byte elementId = listElementIds[sp];
            @SuppressWarnings("unchecked")
            ListTag<Tag<?>> list = (ListTag<Tag<?>>) containers[sp];
            listRemaining[sp] = remaining - 1;

            if (elementId == 10 || elementId == 9) {
                int childDepth = sp + depth + 2;

                if (childDepth >= 512)
                    throw new NbtMaxDepthException();

                int newSp = sp + 1;

                if (newSp >= containers.length) {
                    int grown = Math.min(containers.length << 1, 512);
                    containers = Arrays.copyOf(containers, grown);
                    kinds = Arrays.copyOf(kinds, grown);
                    listElementIds = Arrays.copyOf(listElementIds, grown);
                    listRemaining = Arrays.copyOf(listRemaining, grown);
                }

                Tag<?> child;
                kinds[newSp] = elementId;

                if (elementId == 10) {
                    CompoundTag c = new CompoundTag();
                    containers[newSp] = c;
                    child = c;
                } else {
                    byte listType = this.readByte();
                    int length = Math.max(0, this.readInt());
                    ListTag<Tag<?>> listTag = new ListTag<>(listType, length);
                    containers[newSp] = listTag;
                    listElementIds[newSp] = listType;
                    listRemaining[newSp] = length;
                    child = listTag;
                }

                list.add(child);
                sp = newSp;
                continue;
            }

            list.add(this.readTag(elementId, sp + depth + 1));
        }

        return rootTag;
    }

}
