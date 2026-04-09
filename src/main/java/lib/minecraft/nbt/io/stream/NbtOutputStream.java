package dev.sbs.minecraftapi.nbt.io.stream;

import dev.sbs.minecraftapi.nbt.io.NbtByteCodec;
import dev.sbs.minecraftapi.nbt.io.NbtOutput;
import org.jetbrains.annotations.NotNull;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * NBT serialization that writes directly to an output stream.
 *
 * <p>Modified UTF-8 and the primitive byte-level writes are inherited from {@link DataOutputStream}
 * unchanged. {@code writeListTag} and {@code writeCompoundTag} are inherited from {@link NbtOutput}
 * as default methods - this class only overrides the bulk primitive array writes where a scratch
 * buffer plus {@link NbtByteCodec} is faster than per-element {@code writeInt}/{@code writeLong}
 * method calls through {@link DataOutputStream}.</p>
 */
public class NbtOutputStream extends DataOutputStream implements NbtOutput {

    public NbtOutputStream(@NotNull OutputStream outputStream) {
        super(outputStream);
    }

    @Override
    public void writeByteArray(byte @NotNull [] data) throws IOException {
        this.writeInt(data.length);
        this.write(data);
    }

    @Override
    public void writeIntArray(int @NotNull [] data) throws IOException {
        int length = data.length;
        this.writeInt(length);

        // Encode the whole array into a scratch buffer, then push it through with one write() call.
        byte[] scratch = new byte[length << 2];
        int p = 0;

        for (int i = 0; i < length; i++) {
            NbtByteCodec.putInt(scratch, p, data[i]);
            p += 4;
        }

        this.write(scratch);
    }

    @Override
    public void writeLongArray(long @NotNull [] data) throws IOException {
        int length = data.length;
        this.writeInt(length);

        byte[] scratch = new byte[length << 3];
        int p = 0;

        for (int i = 0; i < length; i++) {
            NbtByteCodec.putLong(scratch, p, data[i]);
            p += 8;
        }

        this.write(scratch);
    }

}
