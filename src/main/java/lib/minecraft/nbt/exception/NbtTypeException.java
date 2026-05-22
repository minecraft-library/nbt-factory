package lib.minecraft.nbt.exception;

import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown when a navigator or accessor is invoked against a tag whose actual type does not match
 * the requested view - calling {@code getInt(int)} on a non-int {@code RawList}, requesting the
 * compound child of a list, or constructing a borrow navigator from a tape kind that does not
 * correspond to a value entry.
 */
public class NbtTypeException extends NbtException {

    public NbtTypeException(@NotNull Throwable cause) {
        super(cause);
    }

    public NbtTypeException(@NotNull String message) {
        super(message);
    }

    public NbtTypeException(@NotNull Throwable cause, @NotNull String message) {
        super(cause, message);
    }

    public NbtTypeException(@NotNull @PrintFormat String message, @Nullable Object... args) {
        super(message, args);
    }

    public NbtTypeException(@NotNull Throwable cause, @NotNull @PrintFormat String message, @Nullable Object... args) {
        super(cause, message, args);
    }

}
