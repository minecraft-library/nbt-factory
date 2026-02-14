package dev.sbs.minecraftapi.nbt.exception;

import dev.sbs.minecraftapi.exception.MinecraftException;
import dev.sbs.minecraftapi.nbt.NbtFactory;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link NbtException NbtExceptions} are thrown when the {@link NbtFactory} class is unable<br>
 * to parse nbt data.
 */
public class NbtException extends MinecraftException {

    public NbtException(@NotNull Throwable cause) {
        super(cause);
    }

    public NbtException(@NotNull String message) {
        super(message);
    }

    public NbtException(@NotNull Throwable cause, @NotNull String message) {
        super(cause, message);
    }

    public NbtException(@NotNull @PrintFormat String message, @Nullable Object... args) {
        super(String.format(message, args));
    }

    public NbtException(@NotNull Throwable cause, @NotNull @PrintFormat String message, @Nullable Object... args) {
        super(String.format(message, args), cause);
    }

}
