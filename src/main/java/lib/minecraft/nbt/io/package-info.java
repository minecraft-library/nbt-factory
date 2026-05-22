/**
 * NBT codec backends and the read / write contracts they share.
 *
 * <p>{@link NbtInput} and {@link NbtOutput} are the two-sided contract every backend in this
 * module implements. Both interfaces split the codec into a backend-specific primitive layer
 * ({@code readByte}/{@code writeByte}, ..., {@code readUTF}/{@code writeUTF}, the typed array
 * reads / writes) and a default structural layer ({@code readTag} / {@code writeTag},
 * {@code readListTag} / {@code writeListTag}, {@code readCompoundTag} / {@code writeCompoundTag})
 * encoded against Minecraft's canonical big-endian binary wire format. Binary backends inherit the
 * structural defaults verbatim; text backends override them because they have no inbound type
 * byte.</p>
 *
 * <h2>Backend layout</h2>
 *
 * <ul>
 *   <li>{@code lib.minecraft.nbt.io.buffer} - heap-backed {@code NbtInputBuffer} /
 *       {@code NbtOutputBuffer}. The primary materializing backend behind
 *       {@link NbtFactory#fromByteArray(byte[])} and {@link NbtFactory#toByteArray(CompoundTag)}.
 *       Drives a {@code byte[]} cursor directly and routes compound-key reads through the
 *       {@code NbtKnownKeys} interning table.</li>
 *   <li>{@code lib.minecraft.nbt.io.stream} - {@code NbtInputStream} / {@code NbtOutputStream}.
 *       Thin {@link DataInputStream} / {@link DataOutputStream} wrappers used by
 *       {@link NbtFactory#fromFile}, {@link NbtFactory#fromResource}, and the streaming write
 *       entry points for payloads larger than the in-memory codec wants to hold.</li>
 *   <li>{@code lib.minecraft.nbt.io.tape} - {@code NbtInputTape}. The tape-building backend that
 *       feeds the zero-allocation {@code lib.minecraft.nbt.tag.borrow} navigator API; reads each
 *       value into a packed {@code long[]} entry rather than a {@link Tag} subtree.</li>
 *   <li>{@code lib.minecraft.nbt.io.snbt} - {@code SnbtSerializer} / {@code SnbtDeserializer}.
 *       The stringified-NBT text codec used by command-line tooling.</li>
 *   <li>{@code lib.minecraft.nbt.io.json} - {@code NbtJsonSerializer} / {@code NbtJsonDeserializer}.
 *       Gson-backed JSON codec for HTTP integrations that prefer JSON over raw NBT.</li>
 *   <li>{@code lib.minecraft.nbt.io.util} - shared codec helpers ({@code NbtByteCodec} big-endian
 *       primitives, {@code NbtModifiedUtf8} string decoder, {@code NbtKnownKeys} interning table,
 *       and the small growable {@code ByteList}/{@code IntList}/{@code LongList} buffers used by
 *       the text deserializers). Internal.</li>
 * </ul>
 *
 * <p>Production callers should route through {@link NbtFactory} rather than touching a backend
 * directly - the factory owns gzip auto-detect, root-tag validation, and the {@code IOException}
 * wrapping convention.</p>
 */
package lib.minecraft.nbt.io;

import lib.minecraft.nbt.NbtFactory;
import lib.minecraft.nbt.tag.CompoundTag;
import lib.minecraft.nbt.tag.Tag;

import java.io.DataInputStream;
import java.io.DataOutputStream;
