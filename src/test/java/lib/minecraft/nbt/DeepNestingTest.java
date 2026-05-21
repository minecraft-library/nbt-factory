package lib.minecraft.nbt;

import lib.minecraft.nbt.exception.NbtException;
import lib.minecraft.nbt.exception.NbtMaxDepthException;
import lib.minecraft.nbt.tags.collection.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Adversarial-input tests for the recursive {@code readCompoundTag}/{@code readListTag} path,
 * which combines the explicit {@code depth >= 512} gate with a defense-in-depth
 * {@code try { ... } catch (StackOverflowError)} rethrow as {@link NbtMaxDepthException}.
 * Verifies that pathologically deep nesting trips {@link NbtMaxDepthException} via
 * {@link NbtException} instead of crashing the JVM with {@link StackOverflowError}.
 *
 * <p>Wire-format constants used by every fixture:</p>
 * <ul>
 *   <li>{@code 0x0A} - {@code TAG_Compound} type byte</li>
 *   <li>{@code 0x00 0x00} - empty modified-UTF-8 name (length-0 short)</li>
 *   <li>{@code 0x0A 0x00 0x01 0x61} - nested compound entry: type {@code 10}, key {@code "a"}</li>
 *   <li>{@code 0x00} - {@code TAG_End} marker closing one compound frame</li>
 * </ul>
 *
 * <p>Cap behaviour: with the public entry point starting at {@code depth = 0}, the first call
 * lands at depth {@code 1}, so {@code N} successfully recursed compounds requires {@code N < 512}.
 * {@code N = 511} parses cleanly; {@code N = 512} trips the cap on the deepest call. Inputs deeper
 * than the cap may trip either the explicit gate or the {@code StackOverflowError} catch depending
 * on the running thread's stack size - both paths surface as {@link NbtMaxDepthException}.</p>
 */
public class DeepNestingTest {

    /**
     * Builds the binary NBT payload for a chain of {@code n} nested {@code TAG_Compound}s, each
     * holding a single key {@code "a"} whose value is the next nested compound, terminated by a
     * sequence of {@code TAG_End} markers - one per frame.
     */
    private static byte[] buildNestedCompoundPayload(int n) {
        // 3-byte root header (type + empty name short) + (n-1) 4-byte nested openers + n 1-byte ENDs.
        byte[] payload = new byte[3 + 4 * (n - 1) + n];
        int p = 0;
        payload[p++] = 0x0A;       // root TAG_Compound type
        payload[p++] = 0x00;       // root name length high byte
        payload[p++] = 0x00;       // root name length low byte (empty name)

        for (int i = 0; i < n - 1; i++) {
            payload[p++] = 0x0A;   // nested entry: TAG_Compound
            payload[p++] = 0x00;   // key length high byte
            payload[p++] = 0x01;   // key length low byte (1)
            payload[p++] = 0x61;   // key "a"
        }

        // n TAG_End markers - one per frame opened (root + n-1 nested).
        for (int i = 0; i < n; i++)
            payload[p++] = 0x00;

        return payload;
    }

    @Test
    @DisplayName("511 nested compounds parses cleanly (right at the cap)")
    void compoundsAtCapParseCleanly() {
        byte[] payload = buildNestedCompoundPayload(511);
        CompoundTag root = NbtFactory.fromByteArray(payload);

        // Walk the chain to confirm structural integrity end-to-end. Each level holds exactly one
        // entry under key "a" until we hit the deepest compound, which is empty.
        CompoundTag current = root;
        for (int level = 1; level < 511; level++) {
            assertEquals(1, current.size(), "level " + level + " should have one child");
            CompoundTag next = (CompoundTag) current.get("a");
            assertNotNull(next, "level " + level + " child must be present");
            current = next;
        }

        assertEquals(0, current.size(), "deepest compound (level 511) should be empty");
    }

    @Test
    @DisplayName("512 nested compounds trips NbtMaxDepthException at the deepest call")
    void compoundsAtCapPlusOneTripCap() {
        byte[] payload = buildNestedCompoundPayload(512);
        NbtException thrown = assertThrows(NbtException.class, () -> NbtFactory.fromByteArray(payload));
        assertInstanceOf(NbtMaxDepthException.class, thrown.getCause(),
                "cause must be NbtMaxDepthException, not StackOverflowError or other");
    }

    @Test
    @DisplayName("600 nested compounds trips NbtMaxDepthException, never StackOverflowError")
    void deeplyNestedCompoundsThrowMaxDepth() {
        byte[] payload = buildNestedCompoundPayload(600);
        NbtException thrown = assertThrows(NbtException.class, () -> NbtFactory.fromByteArray(payload));
        assertInstanceOf(NbtMaxDepthException.class, thrown.getCause(),
                "cause must be NbtMaxDepthException - the recursive parser must never crash on deep input");
    }

    @Test
    @DisplayName("Empty root compound (1 level) parses to an empty CompoundTag")
    void singleLevelCompoundParsesEmpty() {
        byte[] payload = buildNestedCompoundPayload(1);
        CompoundTag root = NbtFactory.fromByteArray(payload);
        assertEquals(0, root.size());
        assertNull(root.get("a"));
    }
}
