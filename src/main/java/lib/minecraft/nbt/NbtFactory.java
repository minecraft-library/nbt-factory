package lib.minecraft.nbt;

import lib.minecraft.nbt.borrow.BorrowedCompoundTag;
import lib.minecraft.nbt.borrow.Tape;
import lib.minecraft.nbt.borrow.TapeParser;
import lib.minecraft.nbt.exception.NbtException;
import lib.minecraft.nbt.io.buffer.NbtInputBuffer;
import lib.minecraft.nbt.io.buffer.NbtOutputBuffer;
import lib.minecraft.nbt.io.json.NbtJsonDeserializer;
import lib.minecraft.nbt.io.json.NbtJsonSerializer;
import lib.minecraft.nbt.io.snbt.SnbtDeserializer;
import lib.minecraft.nbt.io.snbt.SnbtSerializer;
import lib.minecraft.nbt.io.stream.NbtInputStream;
import lib.minecraft.nbt.io.stream.NbtOutputStream;
import lib.minecraft.nbt.tags.TagType;
import lib.minecraft.nbt.tags.collection.CompoundTag;
import dev.simplified.stream.Compression;
import dev.simplified.util.StringUtil;
import dev.simplified.util.SystemUtil;
import lombok.Cleanup;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Standard interface for reading and writing NBT data structures.
 *
 * <p>The materializing surface ({@link #fromByteArray(byte[])} and friends) reconstructs a fully
 * allocated {@link CompoundTag} tree on every call.</p>
 *
 * <p>Read-heavy callers may prefer the zero-allocation borrow API:
 * {@link #borrowFromByteArray(byte[])} returns a {@link BorrowedCompoundTag} that defers MUTF-8
 * decoding and primitive-array materialization until each field is accessed. See the
 * {@code lib.minecraft.nbt.borrow} package for details.</p>
 *
 * @see <a href="https://wiki.vg/NBT">Official NBT Wiki</a>
 * @see <a href="https://minecraft.fandom.com/wiki/NBT_format">Fandom NBT Wiki</a>
 */
@UtilityClass
public class NbtFactory {

    /**
     * Deserializes an NBT Base64 encoded {@link String} into a {@link CompoundTag}.
     *
     * @param encoded the NBT Base64 encoded string to decode.
     * @throws NbtException if any I/O error occurs.
     */
    public @NotNull CompoundTag fromBase64(@NotNull String encoded) throws NbtException {
        return fromByteArray(StringUtil.decodeBase64(encoded));
    }

    /**
     * Deserializes an NBT {@code byte[]} array into a {@link CompoundTag}.
     *
     * @param bytes the {@code byte[]} array to read from.
     * @throws NbtException if any I/O error occurs.
     */
    public @NotNull CompoundTag fromByteArray(byte[] bytes) throws NbtException {
        try {
            byte[] decompressed = Compression.decompress(bytes);
            NbtInputBuffer buffer = new NbtInputBuffer(decompressed);

            if (buffer.readByte() != TagType.COMPOUND.getId())
                throw new IOException("Root tag in NBT structure must be a CompoundTag.");

            buffer.readUTF(); // Discard Root Name
            return buffer.readCompoundTag();
        } catch (Exception exception) {
            throw new NbtException(exception);
        }
    }

    /**
     * Deserializes an NBT {@code byte[]} array into a {@link BorrowedCompoundTag} backed by the
     * (possibly decompressed) input bytes.
     *
     * <p>Mirrors {@link #fromByteArray(byte[])}'s gzip auto-detect via
     * {@link Compression#decompress(byte[])} - raw payloads pass through, gzipped payloads are
     * inflated - then routes the decompressed bytes through
     * {@link TapeParser#parse(byte[])} instead of materializing a {@link CompoundTag}. The returned
     * navigator decodes values lazily as the caller traverses the tree, so payloads where most
     * fields are read once and discarded skip the per-value allocation overhead of the
     * materializing path entirely.</p>
     *
     * <p><b>Buffer-retention contract.</b> The returned {@link BorrowedCompoundTag} holds a strong
     * reference to the decompressed bytes (transitively, through the underlying {@link Tape}).
     * Pointer-kind tape elements address bytes inside that retained array, so the array stays
     * alive as long as any borrowed view derived from this call is reachable. Callers therefore
     * must not assume the input array - or, when gzip is auto-detected, the inflated array - is
     * eligible for garbage collection just because this method has returned. Conversely, dropping
     * the returned navigator is sufficient to release the retained buffer; no explicit
     * {@code close} is required.</p>
     *
     * <p><b>Mutation hazard.</b> Callers must not mutate the input array after invoking this
     * method (and, for gzipped input, must not assume the inflated buffer surfaces anywhere - it
     * does not). Mutating the retained buffer corrupts every pointer-kind tape element addressing
     * it, including subsequent {@link BorrowedCompoundTag#materialize() materialize} calls.</p>
     *
     * <p><b>Thread safety.</b> Decoding is single-threaded - the {@link TapeParser} runs on the
     * calling thread before this method returns. Once returned, the borrow tree is read-only and
     * the underlying {@link Tape} is immutable, so navigation can be parallelized across threads.
     * Note that {@code BorrowedStringTag} caches the materialized {@link String} the first time
     * {@code toString()} or {@code equals} forces decoding; that cache write is not synchronized,
     * so cross-thread first access on the same string node may decode redundantly. The cache is
     * idempotent (every observer sees the same {@link String} value), so this is a performance
     * concern, not a correctness one.</p>
     *
     * <p><b>Escape hatch.</b> Call {@link BorrowedCompoundTag#materialize()} on the returned
     * navigator (or on any descendant) to obtain a fully-allocated {@link CompoundTag} subtree
     * detached from the retained buffer. The materialized tree retains no reference to the input
     * array, so the buffer becomes eligible for collection as soon as every borrowed view is
     * dropped.</p>
     *
     * @param bytes the {@code byte[]} array to read from; gzipped payloads are auto-detected and
     *     decompressed before parsing
     * @return a borrowed view rooted at the input's root compound
     * @throws NbtException if any I/O error occurs - empty or truncated input, gzip header
     *     corruption, malformed binary NBT, or nesting deeper than the parser's 512-frame cap
     */
    @ApiStatus.Experimental
    public @NotNull BorrowedCompoundTag borrowFromByteArray(byte @NotNull [] bytes) throws NbtException {
        try {
            // Mirror fromByteArray's auto-detect: Compression.decompress is a no-op for raw payloads
            // and inflates gzipped ones. Route the decompressed bytes - which the returned tape
            // retains - through TapeParser instead of materializing a CompoundTag.
            byte[] decompressed = Compression.decompress(bytes);
            Tape tape = TapeParser.parse(decompressed);
            return tape.root();
        } catch (Exception exception) {
            throw new NbtException(exception);
        }
    }

    /**
     * Deserializes an NBT {@link File} into a {@link CompoundTag}.
     *
     * <p>Streams directly via {@link NbtInputStream} rather than loading the whole file into a
     * transient {@code byte[]} first - saves one full-file allocation per read.</p>
     *
     * @param file the NBT file to read from.
     * @throws NbtException if any I/O error occurs.
     */
    public @NotNull CompoundTag fromFile(@NotNull File file) throws NbtException {
        try {
            @Cleanup FileInputStream fileInputStream = new FileInputStream(file);
            return fromStream(fileInputStream);
        } catch (IOException exception) {
            throw new NbtException(exception);
        }
    }

    /**
     * Deserializes a JSON {@link File} into a {@link CompoundTag} using the Minecraft Wiki's
     * "Conversion from JSON" algorithm.
     *
     * <p>JSON does not preserve NBT tag type information, so reconstruction applies the rules
     * documented on {@link NbtJsonDeserializer}. This conversion is lossy in the general case -
     * see that class for the type-inference contract and round-trip caveats.</p>
     *
     * @param file the JSON file to read from.
     * @throws NbtException if any I/O or parse error occurs.
     */
    public @NotNull CompoundTag fromJson(@NotNull File file) throws NbtException {
        try {
            @Cleanup FileReader reader = new FileReader(file, StandardCharsets.UTF_8);
            @Cleanup NbtJsonDeserializer deserializer = new NbtJsonDeserializer(reader);
            return deserializer.readCompoundTag(0);
        } catch (IOException exception) {
            throw new NbtException(exception);
        }
    }

    /**
     * Deserializes a JSON {@link String} into a {@link CompoundTag} using the Minecraft Wiki's
     * "Conversion from JSON" algorithm.
     *
     * <p>JSON does not preserve NBT tag type information, so reconstruction applies the rules
     * documented on {@link NbtJsonDeserializer}. This conversion is lossy in the general case -
     * see that class for the type-inference contract and round-trip caveats.</p>
     *
     * @param json the JSON string to read from.
     * @throws NbtException if any I/O or parse error occurs.
     */
    public @NotNull CompoundTag fromJson(@NotNull String json) throws NbtException {
        try {
            @Cleanup NbtJsonDeserializer deserializer = new NbtJsonDeserializer(new StringReader(json));
            return deserializer.readCompoundTag(0);
        } catch (IOException exception) {
            throw new NbtException(exception);
        }
    }

    /**
     * Deserializes an SNBT {@link File} into a {@link CompoundTag}.
     *
     * @param file the SNBT file to read from.
     * @throws NbtException if any I/O error occurs.
     */
    public @NotNull CompoundTag fromSnbt(@NotNull File file) throws NbtException {
        try {
            String snbt = Files.readString(Paths.get(file.toURI()), StandardCharsets.UTF_8);
            return fromSnbt(snbt);
        } catch (IOException exception) {
            throw new NbtException(exception);
        }
    }

    /**
     * Deserializes an SNBT {@link String} into a {@link CompoundTag}.
     *
     * @param snbt the SNBT string to read from.
     * @throws NbtException if any I/O error occurs.
     */
    public @NotNull CompoundTag fromSnbt(@NotNull String snbt) throws NbtException {
        try {
            @Cleanup SnbtDeserializer snbtDeserializer = new SnbtDeserializer(snbt);
            return snbtDeserializer.readCompoundTag(0);
        } catch (IOException exception) {
            throw new NbtException(exception);
        }
    }

    /**
     * Deserializes an NBT {@code Resource} into a {@link CompoundTag}.
     *
     * @param path the NBT resource path to read from.
     * @throws NbtException if any I/O error occurs.
     */
    public @NotNull CompoundTag fromResource(@NotNull String path) {
        try {
            @Cleanup InputStream inputStream = SystemUtil.getResource(path);
            return fromStream(inputStream);
        } catch (Exception exception) {
            throw new NbtException(exception);
        }
    }

    /**
     * Deserializes an NBT {@link InputStream} into a {@link CompoundTag}.
     *
     * <p>Streams the input directly through {@link NbtInputStream}, auto-detecting any compression
     * wrapper via {@link Compression#wrap(InputStream)}. Avoids the transient full-payload
     * {@code byte[]} that {@code inputStream.readAllBytes()} would allocate - safe for arbitrarily
     * large inputs (e.g. worldgen chunk files, player .dat files, socket streams).</p>
     *
     * @param inputStream the NBT input stream to read from.
     * @throws NbtException if any I/O error occurs.
     */
    public @NotNull CompoundTag fromStream(@NotNull InputStream inputStream) throws NbtException {
        // Close-shield the caller's stream so the cascading close() from NbtInputStream ->
        // Compression.wrap() -> ... stops at the boundary instead of closing the caller's
        // InputStream. Preserves the prior contract that callers own the lifetime of their stream.
        InputStream shielded = new FilterInputStream(inputStream) {
            @Override
            public void close() { /* intentionally shielded */ }
        };

        try (
            InputStream decompressed = Compression.wrap(shielded);
            NbtInputStream nbtInputStream = new NbtInputStream(decompressed)
        ) {
            if (nbtInputStream.readByte() != TagType.COMPOUND.getId())
                throw new IOException("Root tag in NBT structure must be a CompoundTag.");

            nbtInputStream.readUTF(); // Discard root name
            return nbtInputStream.readCompoundTag();
        } catch (Exception exception) {
            throw new NbtException(exception);
        }
    }

    /**
     * Deserializes an NBT {@link URL} into a {@link CompoundTag}.
     *
     * @param url the NBT url to read from.
     * @throws NbtException if any I/O error occurs.
     */
    public @NotNull CompoundTag fromUrl(@NotNull URL url) {
        try {
            @Cleanup InputStream inputStream = url.openStream();
            return fromStream(inputStream);
        } catch (IOException exception) {
            throw new NbtException(exception);
        }
    }

    /**
     * Serializes a {@link CompoundTag} into an NBT Base64 encoded {@link String} with {@link Compression#GZIP} compression.
     *
     * @param compound the NBT compound to write.
     * @throws NbtException if any I/O error occurs
     */
    public @NotNull String toBase64(@NotNull CompoundTag compound) throws NbtException {
        return toBase64(compound, Compression.GZIP);
    }

    /**
     * Serializes a {@link CompoundTag} into an NBT Base64 encoded {@link String} with the given compression.
     *
     * @param compound the NBT compound to write.
     * @param compression compression to use on the file.
     * @throws NbtException if any I/O error occurs
     */
    public @NotNull String toBase64(@NotNull CompoundTag compound, @NotNull Compression compression) throws NbtException {
        return StringUtil.encodeBase64ToString(toByteArray(compound, compression));
    }

    /**
     * Serializes a {@link CompoundTag} into an NBT {@code byte[]} array with {@link Compression#NONE NO} compression.
     *
     * @param compound the NBT compound to write.
     * @throws NbtException if any I/O error occurs.
     */
    public byte[] toByteArray(@NotNull CompoundTag compound) throws NbtException {
        return toByteArray(compound, Compression.NONE);
    }

    /**
     * Serializes a {@link CompoundTag} into an NBT {@code byte[]} array with the given compression.
     *
     * @param compound the NBT compound to write.
     * @param compression compression to use on the file.
     * @throws NbtException if any I/O error occurs
     */
    public byte[] toByteArray(@NotNull CompoundTag compound, @NotNull Compression compression) throws NbtException {
        try {
            // Serialize into the growable buffer, then hand the raw backing array straight to
            // Compression.compress(data, offset, length, compression) - this skips the full-payload
            // trimming arraycopy that the old buffer.toByteArray() then Compression.compress(bytes)
            // path performed.
            NbtOutputBuffer buffer = new NbtOutputBuffer();
            buffer.writeByte(TagType.COMPOUND.getId());
            buffer.writeUTF(""); // Empty root name
            buffer.writeCompoundTag(compound);

            return Compression.compress(buffer.rawBuffer(), 0, buffer.size(), compression);
        } catch (Exception exception) {
            throw new NbtException(exception);
        }
    }

    /**
     * Serializes a {@link CompoundTag} into an NBT {@link File} with {@link Compression#GZIP} compression.
     *
     * @param compound the NBT compound to write.
     * @param file the file to write to.
     * @throws NbtException if any I/O error occurs.
     */
    public void toFile(@NotNull CompoundTag compound, @NotNull File file) throws NbtException {
        toFile(compound, file, Compression.GZIP);
    }

    /**
     * Serializes a {@link CompoundTag} into an NBT {@link File} with the given compression.
     *
     * <p>Streams directly through {@link NbtOutputStream} and the compressing wrapper - no
     * transient full-payload {@code byte[]} is allocated.</p>
     *
     * @param compound the NBT compound to write.
     * @param file the file to write to.
     * @param compression compression to use on the file.
     * @throws NbtException if any I/O error occurs.
     */
    public void toFile(@NotNull CompoundTag compound, @NotNull File file, @NotNull Compression compression) throws NbtException {
        try {
            @Cleanup FileOutputStream fileOutputStream = new FileOutputStream(file);
            toStream(compound, fileOutputStream, compression);
        } catch (IOException exception) {
            throw new NbtException(exception);
        }
    }

    /**
     * Serializes a {@link CompoundTag} into a JSON {@link String}.
     *
     * <p>Produces plain JSON with no SNBT-style type suffixes or array headers - see
     * {@link NbtJsonSerializer} for the emitted representation. Pair with {@link #fromJson(String)}
     * to read the result back; the round-trip is lossy in the general case because JSON does not
     * preserve NBT tag type information.</p>
     *
     * @param compound the NBT compound to write.
     * @throws NbtException if any I/O error occurs.
     */
    public @NotNull String toJson(@NotNull CompoundTag compound) throws NbtException {
        try {
            StringWriter writer = new StringWriter();
            NbtJsonSerializer nbtJsonSerializer = new NbtJsonSerializer(writer);
            nbtJsonSerializer.writeCompoundTag(compound);
            return writer.toString();
        } catch (IOException exception) {
            throw new NbtException(exception);
        }
    }

    /**
     * Serializes a {@link CompoundTag} into a JSON {@link File}.
     *
     * <p>Produces plain JSON with no SNBT-style type suffixes or array headers - see
     * {@link NbtJsonSerializer} for the emitted representation. Pair with {@link #fromJson(File)}
     * to read the result back; the round-trip is lossy in the general case because JSON does not
     * preserve NBT tag type information.</p>
     *
     * @param compound the NBT compound to write.
     * @param file the file to write to.
     * @throws NbtException if any I/O error occurs.
     */
    public void toJson(@NotNull CompoundTag compound, @NotNull File file) throws NbtException {
        try {
            @Cleanup FileWriter writer = new FileWriter(file);
            NbtJsonSerializer nbtJsonSerializer = new NbtJsonSerializer(writer);
            nbtJsonSerializer.writeCompoundTag(compound);
        } catch (IOException exception) {
            throw new NbtException(exception);
        }
    }

    /**
     * Serializes a {@link CompoundTag} into a SNBT {@link String}.
     *
     * @param compound the NBT compound to write.
     * @throws NbtException if any I/O error occurs.
     */
    public @NotNull String toSnbt(@NotNull CompoundTag compound) {
        try {
            StringWriter writer = new StringWriter();
            SnbtSerializer snbtSerializer = new SnbtSerializer(writer);
            snbtSerializer.writeCompoundTag(compound);
            return writer.toString();
        } catch (IOException exception) {
            throw new NbtException(exception);
        }
    }

    /**
     * Serializes a {@link CompoundTag} into a SNBT {@link String}.
     *
     * @param compound the NBT compound to write.
     * @param file the file to write to.
     * @throws NbtException if any I/O error occurs.
     */
    public void toSnbt(@NotNull CompoundTag compound, @NotNull File file) {
        try {
            @Cleanup FileWriter writer = new FileWriter(file);
            SnbtSerializer snbtSerializer = new SnbtSerializer(writer);
            snbtSerializer.writeCompoundTag(compound);
        } catch (IOException exception) {
            throw new NbtException(exception);
        }
    }

    /**
     * Serializes a {@link CompoundTag} into an {@link OutputStream} with {@link Compression#GZIP} compression.
     *
     * @param compound the NBT compound to write.
     * @param outputStream the stream to write to.
     * @throws NbtException if any I/O error occurs
     */
    public void toStream(@NotNull CompoundTag compound, @NotNull OutputStream outputStream) throws NbtException {
        toStream(compound, outputStream, Compression.GZIP);
    }

    /**
     * Serializes a {@link CompoundTag} into an {@link OutputStream} with the given compression.
     *
     * <p>Streams the output directly through {@link NbtOutputStream}, wrapping the target stream
     * in the compressing output stream returned by
     * {@link Compression#wrap(OutputStream, Compression)} when compression is requested. Avoids
     * the transient full-payload {@code byte[]} that {@code toByteArray(compound, compression)}
     * would allocate - safe for arbitrarily large compounds and for streaming destinations like
     * sockets and in-flight HTTP response bodies.</p>
     *
     * <p>The target stream is wrapped in a {@link BufferedOutputStream} when not already buffered,
     * so raw {@code FileOutputStream} / socket stream callers do not pay per-byte syscall overhead
     * through {@link NbtOutputStream}. The buffered wrapper plus the NbtOutputStream and the
     * compressing wrapper are all flushed and closed on return; the underlying {@code outputStream}
     * itself is not closed, consistent with the prior behaviour.</p>
     *
     * @param compound the NBT compound to write.
     * @param outputStream the stream to write to.
     * @param compression compression to use on the stream.
     * @throws NbtException if any I/O error occurs
     */
    public void toStream(@NotNull CompoundTag compound, @NotNull OutputStream outputStream, @NotNull Compression compression) throws NbtException {
        // Close-shield the caller's stream so the cascading close() from NbtOutputStream stops at
        // the boundary instead of closing the caller's OutputStream. Preserves the prior contract:
        // caller owns the lifetime of their stream. The shield still forwards flush() so the
        // caller can rely on written bytes being visible after this method returns.
        OutputStream shielded = new FilterOutputStream(outputStream) {
            @Override
            public void close() throws IOException {
                this.out.flush();
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                // FilterOutputStream's default writes byte-by-byte; forward bulk writes intact.
                this.out.write(b, off, len);
            }
        };

        try {
            // Buffer raw streams so NbtOutputStream's small writes don't hit per-byte syscalls.
            // For Compression.NONE, Compression.wrap returns `buffered` unchanged.
            OutputStream buffered = new BufferedOutputStream(shielded);
            OutputStream compressed = Compression.wrap(buffered, compression);

            // Single try-with-resources on the outermost wrapper: closing NbtOutputStream
            // cascades to close `compressed` (finishing GZIP/ZLIB), which cascades to close
            // `buffered` (flushing), which cascades to close `shielded` (flushing the caller's
            // stream without closing it). Single close path, no double-close hazard.
            try (NbtOutputStream nbtOutputStream = new NbtOutputStream(compressed)) {
                nbtOutputStream.writeByte(TagType.COMPOUND.getId());
                nbtOutputStream.writeUTF(""); // Empty root name
                nbtOutputStream.writeCompoundTag(compound);
            }
        } catch (IOException exception) {
            throw new NbtException(exception);
        }
    }


}
