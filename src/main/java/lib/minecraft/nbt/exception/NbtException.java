package lib.minecraft.nbt.exception;

import lib.minecraft.nbt.NbtFactory;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown when the {@link NbtFactory} is unable to parse NBT data.
 */
public class NbtException extends RuntimeException {

    public NbtException(@NotNull Throwable cause) {
        super(cause);
    }

    public NbtException(@NotNull String message) {
        super(message);
    }

    public NbtException(@NotNull Throwable cause, @NotNull String message) {
        super(message, cause);
    }

    public NbtException(@NotNull @PrintFormat String message, @Nullable Object... args) {
        super(String.format(message, args));
    }

    public NbtException(@NotNull Throwable cause, @NotNull @PrintFormat String message, @Nullable Object... args) {
        super(String.format(message, args), cause);
    }

}
