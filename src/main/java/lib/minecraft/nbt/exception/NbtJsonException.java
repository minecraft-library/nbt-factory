package lib.minecraft.nbt.exception;

import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown when the JSON-NBT deserializer encounters input it cannot map to a tag - a heterogeneous
 * array, an unexpected JSON token, an invalid numeric literal, or a JSON {@code null} in a
 * position that NBT does not permit.
 */
public class NbtJsonException extends NbtException {

    public NbtJsonException(@NotNull Throwable cause) {
        super(cause);
    }

    public NbtJsonException(@NotNull String message) {
        super(message);
    }

    public NbtJsonException(@NotNull Throwable cause, @NotNull String message) {
        super(cause, message);
    }

    public NbtJsonException(@NotNull @PrintFormat String message, @Nullable Object... args) {
        super(message, args);
    }

    public NbtJsonException(@NotNull Throwable cause, @NotNull @PrintFormat String message, @Nullable Object... args) {
        super(cause, message, args);
    }

}
