package dev.sbs.minecraftapi.nbt.io;

import dev.sbs.minecraftapi.nbt.exception.NbtMaxDepthException;

public interface MaxDepthIO {

    default int incrementMaxDepth(int depth) {
        if (++depth < 0)
            throw new IllegalArgumentException("Negative depth is not allowed!");

        if (depth >= 512) {
            throw new NbtMaxDepthException();
        }

        return depth;
    }

}
