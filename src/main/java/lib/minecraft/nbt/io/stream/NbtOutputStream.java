package dev.sbs.minecraftapi.nbt.io.stream;

import dev.sbs.minecraftapi.nbt.io.NbtByteCodec;
import dev.sbs.minecraftapi.nbt.io.NbtOutput;
import org.jetbrains.annotations.NotNull;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * NBT serialization that writes Minecraft's canonical big-endian binary wire format directly to
 * an arbitrary {@link OutputStream} - suitable for files, network sockets, and
 * compression-wrapping output streams alike.
 *
 * <p>Emits Java Edition's binary NBT layout exactly as documented on the
 * <a href="https://minecraft.wiki/w/NBT_format">Minecraft Wiki NBT format</a> page. Primitive
 * byte-level writes and the modified-UTF-8 string encoder are inherited unchanged from
 * {@link DataOutputStream}, which natively produces the big-endian bytes and the 2-byte length
 * prefix + modified-UTF-8 framing NBT uses for every string. {@code writeListTag} and
 * {@code writeCompoundTag} are inherited from the {@link NbtOutput} defaults so the
 * {@code (type, name, value)} + {@code TAG_End} compound framing and the
 * {@code element-type + big-endian length} list framing come in for free.</p>
 *
 * <p>The bulk primitive array writes ({@code writeByteArray}, {@code writeIntArray},
 * {@code writeLongArray}) are overridden to encode the whole payload into a scratch buffer
 * via {@link NbtByteCodec} and push it through with a single {@code write} call, skipping the
 * per-element method-call chain the {@code DataOutputStream.writeInt}/{@code writeLong}
 * defaults would take for big arrays.</p>
 *
 * <p>Implements {@link NbtOutput} on top of {@link DataOutputStream} rather than wrapping it so
 * callers can use this directly in either role.</p>
 *
 * @see NbtOutput
 * @see dev.sbs.minecraftapi.nbt.io.array.NbtOutputBuffer
 * @see <a href="https://minecraft.wiki/w/NBT_format">Minecraft Wiki - NBT format</a>
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
