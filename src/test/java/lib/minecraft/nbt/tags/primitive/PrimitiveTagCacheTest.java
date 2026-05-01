package lib.minecraft.nbt.tags.primitive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins the contract of the {@code IntTag.of} / {@code ByteTag.of} / {@code ShortTag.of} /
 * {@code LongTag.of} box caches introduced in Phase D1. Mirrors {@link Integer#valueOf(int)}'s
 * cache semantics: shared instances in {@code [-128, 127]}, fresh allocation outside that range,
 * mutation hazard documented and pinned.
 *
 * <p>The {@code EMPTY} sentinel is intentionally <i>not</i> slotted into any cache: it is an
 * anonymous subclass and {@code Tag.equals} performs a {@code getClass()} check, so substituting
 * it into a parsed tree would break round-trip equality. {@code Foo.of(0)} therefore returns a
 * regular mutable cached instance, not {@code Foo.EMPTY}.</p>
 */
class PrimitiveTagCacheTest {

    // ---------- IntTag ----------

    @Test
    @DisplayName("IntTag.of(0) is cached but is NOT the EMPTY sentinel")
    void intTagOfZeroIsCachedNotEmpty() {
        assertSame(IntTag.of(0), IntTag.of(0));
        assertNotSame(IntTag.EMPTY, IntTag.of(0));
        assertEquals(0, IntTag.of(0).getValue());
        assertEquals(0, IntTag.EMPTY.getValue());
    }

    @Test
    @DisplayName("IntTag.of caches inclusive boundary values 127 and -128")
    void intTagBoundaryCached() {
        assertSame(IntTag.of(127), IntTag.of(127));
        assertSame(IntTag.of(-128), IntTag.of(-128));
        assertSame(IntTag.of(50), IntTag.of(50));
    }

    @Test
    @DisplayName("IntTag.of allocates fresh outside [-128, 127]")
    void intTagOutOfRangeAllocates() {
        assertNotSame(IntTag.of(128), IntTag.of(128));
        assertNotSame(IntTag.of(-129), IntTag.of(-129));
        assertNotSame(IntTag.of(Integer.MAX_VALUE), IntTag.of(Integer.MAX_VALUE));
        assertNotSame(IntTag.of(Integer.MIN_VALUE), IntTag.of(Integer.MIN_VALUE));
    }

    @Test
    @DisplayName("IntTag mutation hazard: setValue on a cached entry leaks across callers")
    void intTagMutationHazard() {
        IntTag tag = IntTag.of(50);
        try {
            tag.setValue(99);
            assertEquals(99, IntTag.of(50).getValue(),
                "Cached IntTag.of(50) is mutable per documented contract");
        } finally {
            // Restore so other tests in the JVM are not poisoned.
            tag.setValue(50);
        }
    }

    @Test
    @DisplayName("IntTag.EMPTY still rejects mutation")
    void intTagEmptyRejectsMutation() {
        assertThrows(UnsupportedOperationException.class, () -> IntTag.EMPTY.setValue(1));
    }

    // ---------- ByteTag ----------

    @Test
    @DisplayName("ByteTag.of(0) is cached but is NOT the EMPTY sentinel")
    void byteTagOfZeroIsCachedNotEmpty() {
        assertSame(ByteTag.of((byte) 0), ByteTag.of((byte) 0));
        assertNotSame(ByteTag.EMPTY, ByteTag.of((byte) 0));
        assertEquals((byte) 0, ByteTag.of((byte) 0).getValue());
        assertEquals((byte) 0, ByteTag.EMPTY.getValue());
    }

    @Test
    @DisplayName("ByteTag.of caches every value in the signed-byte range")
    void byteTagFullRangeCached() {
        for (int v = Byte.MIN_VALUE; v <= Byte.MAX_VALUE; v++) {
            byte b = (byte) v;
            assertSame(ByteTag.of(b), ByteTag.of(b),
                "ByteTag.of(" + v + ") must be cached");
        }
    }

    @Test
    @DisplayName("ByteTag mutation hazard: setValue on a cached entry leaks across callers")
    void byteTagMutationHazard() {
        ByteTag tag = ByteTag.of((byte) 42);
        try {
            tag.setValue((byte) 7);
            assertEquals((byte) 7, ByteTag.of((byte) 42).getValue());
        } finally {
            tag.setValue((byte) 42);
        }
    }

    @Test
    @DisplayName("ByteTag.EMPTY still rejects mutation")
    void byteTagEmptyRejectsMutation() {
        assertThrows(UnsupportedOperationException.class, () -> ByteTag.EMPTY.setValue((byte) 1));
    }

    // ---------- ShortTag ----------

    @Test
    @DisplayName("ShortTag.of(0) is cached but is NOT the EMPTY sentinel")
    void shortTagOfZeroIsCachedNotEmpty() {
        assertSame(ShortTag.of((short) 0), ShortTag.of((short) 0));
        assertNotSame(ShortTag.EMPTY, ShortTag.of((short) 0));
        assertEquals((short) 0, ShortTag.of((short) 0).getValue());
        assertEquals((short) 0, ShortTag.EMPTY.getValue());
    }

    @Test
    @DisplayName("ShortTag.of caches inclusive boundary values 127 and -128")
    void shortTagBoundaryCached() {
        assertSame(ShortTag.of((short) 127), ShortTag.of((short) 127));
        assertSame(ShortTag.of((short) -128), ShortTag.of((short) -128));
        assertSame(ShortTag.of((short) 50), ShortTag.of((short) 50));
    }

    @Test
    @DisplayName("ShortTag.of allocates fresh outside [-128, 127]")
    void shortTagOutOfRangeAllocates() {
        assertNotSame(ShortTag.of((short) 128), ShortTag.of((short) 128));
        assertNotSame(ShortTag.of((short) -129), ShortTag.of((short) -129));
        assertNotSame(ShortTag.of(Short.MAX_VALUE), ShortTag.of(Short.MAX_VALUE));
        assertNotSame(ShortTag.of(Short.MIN_VALUE), ShortTag.of(Short.MIN_VALUE));
    }

    @Test
    @DisplayName("ShortTag mutation hazard: setValue on a cached entry leaks across callers")
    void shortTagMutationHazard() {
        ShortTag tag = ShortTag.of((short) 33);
        try {
            tag.setValue((short) 11);
            assertEquals((short) 11, ShortTag.of((short) 33).getValue());
        } finally {
            tag.setValue((short) 33);
        }
    }

    @Test
    @DisplayName("ShortTag.EMPTY still rejects mutation")
    void shortTagEmptyRejectsMutation() {
        assertThrows(UnsupportedOperationException.class, () -> ShortTag.EMPTY.setValue((short) 1));
    }

    // ---------- LongTag ----------

    @Test
    @DisplayName("LongTag.of(0) is cached but is NOT the EMPTY sentinel")
    void longTagOfZeroIsCachedNotEmpty() {
        assertSame(LongTag.of(0L), LongTag.of(0L));
        assertNotSame(LongTag.EMPTY, LongTag.of(0L));
        assertEquals(0L, LongTag.of(0L).getValue());
        assertEquals(0L, LongTag.EMPTY.getValue());
    }

    @Test
    @DisplayName("LongTag.of caches inclusive boundary values 127 and -128")
    void longTagBoundaryCached() {
        assertSame(LongTag.of(127L), LongTag.of(127L));
        assertSame(LongTag.of(-128L), LongTag.of(-128L));
        assertSame(LongTag.of(50L), LongTag.of(50L));
    }

    @Test
    @DisplayName("LongTag.of allocates fresh outside [-128, 127]")
    void longTagOutOfRangeAllocates() {
        assertNotSame(LongTag.of(128L), LongTag.of(128L));
        assertNotSame(LongTag.of(-129L), LongTag.of(-129L));
        assertNotSame(LongTag.of(Long.MAX_VALUE), LongTag.of(Long.MAX_VALUE));
        assertNotSame(LongTag.of(Long.MIN_VALUE), LongTag.of(Long.MIN_VALUE));
    }

    @Test
    @DisplayName("LongTag mutation hazard: setValue on a cached entry leaks across callers")
    void longTagMutationHazard() {
        LongTag tag = LongTag.of(77L);
        try {
            tag.setValue(13L);
            assertEquals(13L, LongTag.of(77L).getValue());
        } finally {
            tag.setValue(77L);
        }
    }

    @Test
    @DisplayName("LongTag.EMPTY still rejects mutation")
    void longTagEmptyRejectsMutation() {
        assertThrows(UnsupportedOperationException.class, () -> LongTag.EMPTY.setValue(1L));
    }

}
