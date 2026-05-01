package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.exception.NbtException;
import lib.minecraft.nbt.io.NbtByteCodec;
import lib.minecraft.nbt.io.NbtModifiedUtf8;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.UTFDataFormatException;

/**
 * Package-private decode helpers shared by the borrow-mode navigators.
 *
 * <p>Lifts the modified-UTF-8 decode + length-prefix bookkeeping out of every navigator type so
 * the call site stays a single line. Phase C4's {@code MutfStringView} will replace
 * {@link #decodeUtf8At(byte[], int)} for {@link BorrowedStringTag} and {@link BorrowedCompoundTag}
 * key comparisons - the helper stays in place for the materializing fallback paths the parity test
 * still hits.</p>
 */
@ApiStatus.Internal
@UtilityClass
class BorrowedTagSupport {

    /**
     * Decodes a length-prefixed modified-UTF-8 string at {@code offset} in {@code buffer}.
     *
     * <p>Mirrors the same helper inside {@link Tape}, kept private there to preserve C1's API
     * surface. Wraps the checked {@link UTFDataFormatException} as
     * {@link lib.minecraft.nbt.exception.NbtException} - corruption here means the retained buffer
     * was tampered with, not user input.</p>
     *
     * @param buffer retained tape buffer
     * @param offset byte offset of the 2-byte big-endian length prefix
     * @return the decoded string
     * @throws lib.minecraft.nbt.exception.NbtException if the bytes are not valid modified UTF-8
     */
    static @NotNull String decodeUtf8At(byte @NotNull [] buffer, int offset) {
        int len = NbtByteCodec.getUnsignedShort(buffer, offset);
        try {
            return NbtModifiedUtf8.decode(buffer, offset + 2, len);
        } catch (UTFDataFormatException exception) {
            throw new NbtException(
                exception, "Malformed modified UTF-8 in tape buffer at offset %d", offset);
        }
    }

}
