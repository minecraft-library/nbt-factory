package dev.sbs.minecraftapi.nbt.exception;

import dev.sbs.minecraftapi.exception.MinecraftException;
import dev.sbs.minecraftapi.nbt.NbtFactory;
import org.jetbrains.annotations.NotNull;

/**
 * {@link NbtException NbtExceptions} are thrown when the {@link NbtFactory} class is unable<br>
 * to parse nbt data.
 */
public class NbtException extends MinecraftException {

    public NbtException(@NotNull Exception exception) {
        super(exception);
    }

    public NbtException(@NotNull String message) {
        super(message);
    }

}
