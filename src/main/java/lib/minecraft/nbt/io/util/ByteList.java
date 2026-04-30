package lib.minecraft.nbt.io.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Tiny growable {@code byte[]} buffer used during typed-array deserialization to avoid
 * {@link Byte} boxing.
 *
 * <p>Starts at a 16-element backing array and doubles on overflow. Calls to {@link #toArray()}
 * return a fresh trimmed copy sized exactly to the appended length, so the buffer remains
 * reusable after the read.</p>
 *
 * <p>Internal to the NBT codecs - shared between the SNBT and JSON deserializers so the typed
 * byte-array reader does not allocate per-element {@link Byte} wrappers.</p>
 */
@ApiStatus.Internal
public final class ByteList {

    private byte[] data = new byte[16];
    private int size = 0;

    /**
     * Appends one byte, doubling the backing array if it is at capacity.
     *
     * @param value the byte to append
     */
    public void add(byte value) {
        if (this.size == this.data.length)
            this.data = Arrays.copyOf(this.data, this.data.length << 1);

        this.data[this.size++] = value;
    }

    /**
     * Returns a freshly-allocated {@code byte[]} sized to the number of appended elements.
     *
     * @return a trimmed copy of the appended bytes
     */
    public byte @NotNull [] toArray() {
        return Arrays.copyOf(this.data, this.size);
    }

}
