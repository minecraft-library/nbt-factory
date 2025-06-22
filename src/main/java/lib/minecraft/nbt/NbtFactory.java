package dev.sbs.minecraftapi.nbt;

import dev.sbs.minecraftapi.nbt.exception.NbtException;
import dev.sbs.minecraftapi.nbt.io.json.NbtJsonSerializer;
import dev.sbs.minecraftapi.nbt.io.snbt.SnbtDeserializer;
import dev.sbs.minecraftapi.nbt.io.snbt.SnbtSerializer;
import dev.sbs.minecraftapi.nbt.io.stream.NbtInputStream;
import dev.sbs.minecraftapi.nbt.io.stream.NbtOutputStream;
import dev.sbs.minecraftapi.nbt.tags.TagType;
import dev.sbs.minecraftapi.nbt.tags.collection.CompoundTag;
import dev.sbs.api.util.PrimitiveUtil;
import dev.sbs.api.util.StringUtil;
import dev.sbs.api.util.SystemUtil;
import dev.sbs.api.io.ByteArrayDataInput;
import dev.sbs.api.io.ByteArrayDataOutput;
import dev.sbs.api.io.Compression;
import lombok.Cleanup;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedOutputStream;
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
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

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
            @Cleanup ByteArrayDataInput byteArrayInputStream = new ByteArrayDataInput(bytes);
            return this.fromStream(byteArrayInputStream);
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
            @Cleanup NbtInputStream nbtInputStream = new NbtInputStream(inputStream);
            return this.fromStream(nbtInputStream);
        } catch (IOException exception) {
            throw new NbtException(exception);
        }
    }

    /**
     * Deserializes an NBT {@link NbtInputStream} into a {@link CompoundTag}.
     * <br><br>
     * When creating a new {@link NbtInputStream}, it will automatically wrap the stream
     * with {@link GZIPInputStream} or {@link InflaterInputStream}.
     *
     * @param inputStream the NBT input stream to read from.
     * @throws NbtException if any I/O error occurs.
     */
    public @NotNull CompoundTag fromStream(@NotNull NbtInputStream inputStream) throws NbtException {
        try {
            if (inputStream.readByte() != TagType.COMPOUND.getId())
                throw new IOException("Root tag in NBT structure must be a CompoundTag.");

            inputStream.readUTF(); // Discard Root Name
            return inputStream.readCompoundTag();
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
     * Serializes a {@link CompoundTag} into an NBT Base64 encoded {@link String}.
     *
     * @param compound the NBT compound to write.
     * @throws NbtException if any I/O error occurs.
     */
    public @NotNull String toBase64(@NotNull CompoundTag compound) throws NbtException {
        return StringUtil.encodeBase64ToString(this.toByteArray(compound));
    }

    /**
     * Serializes a {@link CompoundTag} into an NBT {@code byte[]} array.
     *
     * @param compound the NBT compound to write.
     * @throws NbtException if any I/O error occurs.
     */
    public byte[] toByteArray(@NotNull CompoundTag compound) throws NbtException {
        try {
            @Cleanup ByteArrayDataOutput byteArrayDataOutput = new ByteArrayDataOutput();
            this.toStream(compound, byteArrayDataOutput);
            return byteArrayDataOutput.toByteArray();
        } catch (Exception exception) {
            throw new NbtException(exception);
        }
    }

    /**
     * Serializes a {@link CompoundTag} into an NBT {@link File} with {@link Compression#NONE NO} compression.
     *
     * @param compound the NBT compound to write.
     * @param file the file to write to.
     * @throws NbtException if any I/O error occurs.
     */
    public void toFile(@NotNull CompoundTag compound, @NotNull File file) throws NbtException {
        this.toFile(compound, file, Compression.NONE);
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
            @Cleanup OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file));

            outputStream = switch (compression) {
                case GZIP -> new GZIPOutputStream(outputStream);
                case ZLIB -> new DeflaterOutputStream(outputStream);
                default -> outputStream;
            };

            this.toStream(compound, outputStream);
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
     * Serializes a {@link CompoundTag} into an {@link OutputStream}.
     *
     * @param compound the NBT compound to write.
     * @param outputStream the stream to write to.
     * @throws NbtException if any I/O error occurs.
     */
    public void toStream(@NotNull CompoundTag compound, @NotNull OutputStream outputStream) throws NbtException {
        this.toStream(compound, new NbtOutputStream(outputStream));
    }


    /**
     * Serializes a {@link CompoundTag} into an {@link NbtOutputStream}.
     *
     * @param compound the NBT compound to write.
     * @param outputStream the stream to write to.
     * @throws NbtException if any I/O error occurs.
     */
    public void toStream(@NotNull CompoundTag compound, @NotNull NbtOutputStream outputStream) throws NbtException {
        try {
            outputStream.writeByte(TagType.COMPOUND.getId());
            outputStream.writeUTF(""); // Empty Root Name
            outputStream.writeCompoundTag(compound);
        } catch (IOException exception) {
            throw new NbtException(exception);
        }
    }

}
