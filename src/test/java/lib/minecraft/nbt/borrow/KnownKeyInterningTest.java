package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.NbtFactory;
import lib.minecraft.nbt.io.NbtKnownKeys;
import lib.minecraft.nbt.tags.Tag;
import lib.minecraft.nbt.tags.collection.CompoundTag;
import lib.minecraft.nbt.tags.primitive.ByteTag;
import lib.minecraft.nbt.tags.primitive.IntTag;
import lib.minecraft.nbt.tags.primitive.StringTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase D2 regression test for the borrow API's {@link NbtKnownKeys} integration.
 *
 * <p>Asserts that {@link BorrowedCompoundTag#materialize()} and the entry iterator both return
 * canonical interned references for keys that live in the {@link NbtKnownKeys} table, while
 * keys outside the vocabulary still decode correctly through the modified-UTF-8 fallback path.
 * The parity assertion at the end keeps {@code borrow.materialize().equals(NbtFactory.fromByteArray(payload))}
 * holding for compounds that mix known and unknown keys.</p>
 */
class KnownKeyInterningTest {

    /**
     * Picks the canonical {@link NbtKnownKeys} reference for a given key. The lookup table
     * matches by raw byte content, so feeding the key's UTF-8 bytes back through {@code match}
     * yields the same {@link String} instance the borrow API will hand back on a hit. Any miss
     * indicates the key is not in the vocabulary and the test treats that as a setup error.
     *
     * @param key ASCII key expected to live in the table
     * @return the canonical interned {@link String} reference
     */
    private static String canonical(String key) {
        byte[] bytes = key.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        String hit = NbtKnownKeys.match(bytes, 0, bytes.length);
        assertNotNull(hit, "key '" + key + "' is not in NbtKnownKeys");
        return hit;
    }

    @Test
    @DisplayName("materialize() returns identity-equal interned strings for hot keys")
    void materializeReturnsInternedStringsForHotKeys() throws IOException {
        CompoundTag itemTag = new CompoundTag();
        itemTag.put("Damage", IntTag.of(0));
        itemTag.put("Unbreakable", ByteTag.of((byte) 1));

        CompoundTag root = new CompoundTag();
        root.put("id", new StringTag("minecraft:diamond_sword"));
        root.put("Count", ByteTag.of((byte) 1));
        root.put("tag", itemTag);

        byte[] payload = NbtFactory.toByteArray(root);
        BorrowedCompoundTag borrowed = NbtFactory.borrowFromByteArray(payload);
        CompoundTag materialized = borrowed.materialize();

        // Walk the materialized map's keys and assert every hot key is == to its canonical
        // NbtKnownKeys reference. Map.keySet() preserves the same key strings the materializer
        // put() into the compound, so identity carries through.
        Map<String, String> seen = new HashMap<>();
        for (String key : materialized.entrySet().stream().map(Map.Entry::getKey).toList())
            seen.put(key, key);

        assertSame(canonical("id"), seen.get("id"), "id must be interned");
        assertSame(canonical("Count"), seen.get("Count"), "Count must be interned");
        assertSame(canonical("tag"), seen.get("tag"), "tag must be interned");

        // Nested compound keys should also be interned.
        Tag<?> tagSubtree = materialized.get("tag");
        assertTrue(tagSubtree instanceof CompoundTag, "expected tag to materialize as compound");

        CompoundTag tagCompound = (CompoundTag) tagSubtree;
        Map<String, String> nestedSeen = new HashMap<>();
        for (String key : tagCompound.entrySet().stream().map(Map.Entry::getKey).toList())
            nestedSeen.put(key, key);

        assertSame(canonical("Damage"), nestedSeen.get("Damage"), "nested Damage must be interned");
        assertSame(canonical("Unbreakable"), nestedSeen.get("Unbreakable"), "nested Unbreakable must be interned");
    }

    @Test
    @DisplayName("entries() iterator returns identity-equal interned strings for hot keys")
    void entriesIteratorReturnsInternedStringsForHotKeys() throws IOException {
        CompoundTag root = new CompoundTag();
        root.put("id", new StringTag("minecraft:stone"));
        root.put("Count", ByteTag.of((byte) 64));
        root.put("ench", IntTag.of(0));

        byte[] payload = NbtFactory.toByteArray(root);
        BorrowedCompoundTag borrowed = NbtFactory.borrowFromByteArray(payload);

        Map<String, String> seen = new HashMap<>();
        for (Map.Entry<String, BorrowedTag<?>> entry : borrowed.entries())
            seen.put(entry.getKey(), entry.getKey());

        assertSame(canonical("id"), seen.get("id"));
        assertSame(canonical("Count"), seen.get("Count"));
        assertSame(canonical("ench"), seen.get("ench"));
    }

    @Test
    @DisplayName("unknown keys decode through the fallback path and are not interned")
    void unknownKeysDecodeCorrectlyAndAreNotInterned() throws IOException {
        CompoundTag root = new CompoundTag();
        root.put("id", new StringTag("minecraft:custom"));
        root.put("my_custom_key", IntTag.of(42));

        byte[] payload = NbtFactory.toByteArray(root);
        BorrowedCompoundTag borrowed = NbtFactory.borrowFromByteArray(payload);
        CompoundTag materialized = borrowed.materialize();

        // The known key still interns.
        Map<String, String> seen = new HashMap<>();
        for (String key : materialized.entrySet().stream().map(Map.Entry::getKey).toList())
            seen.put(key, key);

        assertSame(canonical("id"), seen.get("id"));

        // The unknown key decoded by value, not identity. Use equals() to find the entry.
        assertTrue(materialized.containsKey("my_custom_key"), "unknown key must round-trip by value");
        assertEquals(42, ((IntTag) materialized.get("my_custom_key")).getValue());

        // And NbtKnownKeys.match must not have a bucket entry for it.
        byte[] keyBytes = "my_custom_key".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        assertNotNull(seen.get("my_custom_key"), "iterator must return the decoded key");
        assertFalse(NbtKnownKeys.match(keyBytes, 0, keyBytes.length) != null,
            "my_custom_key must not be a known key");
    }

    @Test
    @DisplayName("borrow.materialize().equals(NbtFactory.fromByteArray(payload)) holds across mixed keys")
    void borrowMaterializeMatchesProductionOnMixedKeys() throws IOException {
        CompoundTag root = new CompoundTag();
        root.put("id", new StringTag("minecraft:diamond_sword"));
        root.put("Count", ByteTag.of((byte) 1));
        root.put("my_custom_key", IntTag.of(7));

        byte[] payload = NbtFactory.toByteArray(root);

        CompoundTag viaBorrow = NbtFactory.borrowFromByteArray(payload).materialize();
        CompoundTag viaProduction = NbtFactory.fromByteArray(payload);

        assertEquals(viaProduction, viaBorrow);
    }

}
