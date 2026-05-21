package lib.minecraft.nbt.exception;

import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown when binary NBT input is structurally invalid - malformed modified-UTF-8, an unknown
 * tag id at a wire-dispatch site, a tape header pointing past the end of the buffer, or any
 * other corruption detected during parse.
 */
public class NbtFormatException extends NbtException {

    public NbtFormatException(@NotNull Throwable cause) {
        super(cause);
    }

    public NbtFormatException(@NotNull String message) {
        super(message);
    }

    public NbtFormatException(@NotNull Throwable cause, @NotNull String message) {
        super(cause, message);
    }

    public NbtFormatException(@NotNull @PrintFormat String message, @Nullable Object... args) {
        super(message, args);
    }

    public NbtFormatException(@NotNull Throwable cause, @NotNull @PrintFormat String message, @Nullable Object... args) {
        super(cause, message, args);
    }

}
