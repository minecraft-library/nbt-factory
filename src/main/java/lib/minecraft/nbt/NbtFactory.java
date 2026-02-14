package dev.sbs.minecraftapi.nbt;

import dev.sbs.api.io.stream.Compression;
import dev.sbs.api.util.PrimitiveUtil;
import dev.sbs.api.util.StringUtil;
import dev.sbs.api.util.SystemUtil;
import dev.sbs.minecraftapi.nbt.exception.NbtException;
import dev.sbs.minecraftapi.nbt.io.array.NbtInputBuffer;
import dev.sbs.minecraftapi.nbt.io.array.NbtOutputBuffer;
import dev.sbs.minecraftapi.nbt.io.json.NbtJsonSerializer;
import dev.sbs.minecraftapi.nbt.io.snbt.SnbtDeserializer;
import dev.sbs.minecraftapi.nbt.io.snbt.SnbtSerializer;
import dev.sbs.minecraftapi.nbt.tags.TagType;
import dev.sbs.minecraftapi.nbt.tags.collection.CompoundTag;
import lombok.Cleanup;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Standard interface for reading and writing NBT data structures.
 *
 * @see <a href="https://wiki.vg/NBT">Official NBT Wiki</a>
 * @see <a href="https://minecraft.fandom.com/wiki/NBT_format">Fandom NBT Wiki</a>
 */
@Getter
public class NbtFactory {

    /**
     * Deserializes an NBT Base64 encoded {@link String} into a {@link CompoundTag}.
     *
     * @param encoded the NBT Base64 encoded string to decode.
     * @throws NbtException if any I/O error occurs.
     */
    public @NotNull CompoundTag fromBase64(@NotNull String encoded) throws NbtException {
        return this.fromByteArray(StringUtil.decodeBase64(encoded));
    }

    /**
     * Deserializes an NBT {@code Byte[]} array into a {@link CompoundTag}.
     *
     * @param bytes the {@code Byte[]} array to read from.
     * @throws NbtException if any I/O error occurs.
     */
    public @NotNull CompoundTag fromByteArray(@NotNull Byte[] bytes) throws NbtException {
        return this.fromByteArray(PrimitiveUtil.unwrap(bytes));
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
     * Deserializes an NBT {@link File} into a {@link CompoundTag}.
     *
     * @param file the NBT file to read from.
     * @throws NbtException if any I/O error occurs.
     */
    public @NotNull CompoundTag fromFile(@NotNull File file) throws NbtException {
        try {
            @Cleanup FileInputStream fileInputStream = new FileInputStream(file);
            return this.fromStream(fileInputStream);
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
            return this.fromSnbt(snbt);
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
            return this.fromStream(inputStream);
        } catch (Exception exception) {
            throw new NbtException(exception);
        }
    }

    /**
     * Deserializes an NBT {@link InputStream} into a {@link CompoundTag}.
     *
     * @param inputStream the NBT input stream to read from.
     * @throws NbtException if any I/O error occurs.
     */
    public @NotNull CompoundTag fromStream(@NotNull InputStream inputStream) throws NbtException {
        try {
            @Cleanup ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int bytesRead;

            while ((bytesRead = inputStream.read(data, 0, data.length)) != -1)
                buffer.write(data, 0, bytesRead);

            return this.fromByteArray(buffer.toByteArray());
        } catch (IOException exception) {
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
            return this.fromStream(inputStream);
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
        return this.toBase64(compound, Compression.GZIP);
    }

    /**
     * Serializes a {@link CompoundTag} into an NBT Base64 encoded {@link String} with the given compression.
     *
     * @param compound the NBT compound to write.
     * @param compression compression to use on the file.
     * @throws NbtException if any I/O error occurs
     */
    public @NotNull String toBase64(@NotNull CompoundTag compound, @NotNull Compression compression) throws NbtException {
        return StringUtil.encodeBase64ToString(this.toByteArray(compound, compression));
    }

    /**
     * Serializes a {@link CompoundTag} into an NBT {@code byte[]} array with {@link Compression#GZIP} compression.
     *
     * @param compound the NBT compound to write.
     * @throws NbtException if any I/O error occurs.
     */
    public byte[] toByteArray(@NotNull CompoundTag compound) throws NbtException {
        return this.toByteArray(compound, Compression.GZIP);
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
            // Serialize to uncompressed byte array
            NbtOutputBuffer buffer = new NbtOutputBuffer();
            buffer.writeByte(TagType.COMPOUND.getId());
            buffer.writeUTF(""); // Empty root name
            buffer.writeCompoundTag(compound);
            byte[] uncompressed = buffer.toByteArray();

            // Apply compression if needed
            if (compression == Compression.NONE)
                return uncompressed;

            return Compression.compress(uncompressed, compression);
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
        this.toFile(compound, file, Compression.GZIP);
    }

    /**
     * Serializes a {@link CompoundTag} into an NBT {@link File} with the given compression.
     *
     * @param compound the NBT compound to write.
     * @param file the file to write to.
     * @param compression compression to use on the file.
     * @throws NbtException if any I/O error occurs.
     */
    public void toFile(@NotNull CompoundTag compound, @NotNull File file, @NotNull Compression compression) throws NbtException {
        try {
            byte[] data = this.toByteArray(compound, compression);
            @Cleanup FileOutputStream fileOutputStream = new FileOutputStream(file);
            fileOutputStream.write(data);
        } catch (IOException exception) {
            throw new NbtException(exception);
        }

    }

    /**
     * Serializes a {@link CompoundTag} into a JSON {@link String}.
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
        this.toStream(compound, outputStream, Compression.GZIP);
    }

    /**
     * Serializes a {@link CompoundTag} into an {@link OutputStream} with the given compression.
     *
     * @param compound the NBT compound to write.
     * @param outputStream the stream to write to.
     * @param compression compression to use on the stream.
     * @throws NbtException if any I/O error occurs
     */
    public void toStream(@NotNull CompoundTag compound, @NotNull OutputStream outputStream, @NotNull Compression compression) throws NbtException {
        try {
            byte[] data = this.toByteArray(compound, compression);
            outputStream.write(data);
        } catch (IOException exception) {
            throw new NbtException(exception);
        }
    }


}
