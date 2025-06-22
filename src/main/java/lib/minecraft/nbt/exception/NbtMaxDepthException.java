package dev.sbs.minecraftapi.nbt.exception;

/**
 * Checked exception thrown after reaching maximum depth deserializing an NBT tag.
 */
public class NbtMaxDepthException extends NbtException {

    public NbtMaxDepthException() {
        super("Maximum CompoundTag depth of 512 has been reached!");
    }

}
