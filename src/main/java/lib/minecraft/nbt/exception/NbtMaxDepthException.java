package dev.sbs.minecraftapi.nbt.exception;

/**
 * Thrown when the maximum nesting depth is reached while deserializing an NBT tag.
 */
public class NbtMaxDepthException extends NbtException {

    public NbtMaxDepthException() {
        super("Maximum CompoundTag depth of 512 has been reached!");
    }

}
