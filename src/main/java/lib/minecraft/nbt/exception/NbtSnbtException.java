package lib.minecraft.nbt.exception;

import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown when the SNBT parser encounters input that does not satisfy the stringified-NBT grammar -
 * an unterminated string literal, an unknown typed-array marker, a missing value-indicator inside
 * a compound, or any other syntax violation.
 */
public class NbtSnbtException extends NbtException {

    public NbtSnbtException(@NotNull Throwable cause) {
        super(cause);
    }

    public NbtSnbtException(@NotNull String message) {
        super(message);
    }

    public NbtSnbtException(@NotNull Throwable cause, @NotNull String message) {
        super(cause, message);
    }

    public NbtSnbtException(@NotNull @PrintFormat String message, @Nullable Object... args) {
        super(message, args);
    }

    public NbtSnbtException(@NotNull Throwable cause, @NotNull @PrintFormat String message, @Nullable Object... args) {
        super(cause, message, args);
    }

}
