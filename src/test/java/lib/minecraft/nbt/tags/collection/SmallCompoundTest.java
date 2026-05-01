package lib.minecraft.nbt.tags.collection;

import lib.minecraft.nbt.tags.Tag;
import lib.minecraft.nbt.tags.TagType;
import lib.minecraft.nbt.tags.primitive.ByteTag;
import lib.minecraft.nbt.tags.primitive.IntTag;
import lib.minecraft.nbt.tags.primitive.StringTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that a small-mode {@link CompoundTag} (under the 16-entry promotion threshold) honours
 * every {@link Map} contract and matches a {@link LinkedHashMap}-backed reference compound's
 * observable behaviour.
 */
class SmallCompoundTest {

    private static CompoundTag fivesEntries() {
        CompoundTag compound = new CompoundTag();
        compound.put("first", new IntTag(1));
        compound.put("second", new ByteTag((byte) 2));
        compound.put("third", new StringTag("three"));
        compound.put("fourth", new IntTag(4));
        compound.put("fifth", new ByteTag((byte) 5));
        return compound;
    }

    @Test
    void freshCompoundStartsInSmallMode() {
        CompoundTag compound = new CompoundTag();
        assertTrue(compound.isSmallMode(), "default constructor should start in small mode");
        assertEquals(0, compound.size());
        assertTrue(compound.isEmpty());
    }

    @Test
    void sizedConstructorRespectsThreshold() {
        assertTrue(new CompoundTag(0).isSmallMode());
        assertTrue(new CompoundTag(8).isSmallMode());
        assertTrue(new CompoundTag(16).isSmallMode());
        assertFalse(new CompoundTag(17).isSmallMode(), "expectedSize=17 must skip small mode");
        assertFalse(new CompoundTag(64).isSmallMode());
    }

    @Test
    void getReturnsInsertedValues() {
        CompoundTag compound = fivesEntries();
        assertTrue(compound.isSmallMode());
        assertEquals(new IntTag(1), compound.get("first"));
        assertEquals(new ByteTag((byte) 2), compound.get("second"));
        assertEquals(new StringTag("three"), compound.get("third"));
        assertEquals(new IntTag(4), compound.get("fourth"));
        assertEquals(new ByteTag((byte) 5), compound.get("fifth"));
        assertNull(compound.get("missing"));
    }

    @Test
    void putOverwritesExistingKeyAndReturnsPrevious() {
        CompoundTag compound = fivesEntries();
        Tag<?> previous = compound.put("third", new IntTag(33));
        assertEquals(new StringTag("three"), previous);
        assertEquals(new IntTag(33), compound.get("third"));
        assertEquals(5, compound.size(), "size must not change on overwrite");
        assertTrue(compound.isSmallMode());
    }

    @Test
    void containsKeyMatchesReference() {
        CompoundTag compound = fivesEntries();
        Map<String, Tag<?>> reference = new LinkedHashMap<>();
        reference.put("first", new IntTag(1));
        reference.put("second", new ByteTag((byte) 2));
        reference.put("third", new StringTag("three"));
        reference.put("fourth", new IntTag(4));
        reference.put("fifth", new ByteTag((byte) 5));

        for (String key : new String[]{"first", "fifth", "missing", "third", ""}) {
            assertEquals(reference.containsKey(key), compound.containsKey(key), "key=" + key);
        }
    }

    @Test
    void removePreservesInsertionOrder() {
        CompoundTag compound = fivesEntries();
        Tag<?> removed = compound.remove("third");
        assertEquals(new StringTag("three"), removed);
        assertEquals(4, compound.size());
        assertTrue(compound.isSmallMode());

        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, Tag<?>> entry : compound) {
            keys.add(entry.getKey());
        }
        assertEquals(List.of("first", "second", "fourth", "fifth"), keys);
    }

    @Test
    void removeOfMissingKeyReturnsNull() {
        CompoundTag compound = fivesEntries();
        assertNull(compound.remove("missing"));
        assertEquals(5, compound.size());
    }

    @Test
    void clearResetsToEmpty() {
        CompoundTag compound = fivesEntries();
        compound.clear();
        assertEquals(0, compound.size());
        assertTrue(compound.isEmpty());
        assertTrue(compound.isSmallMode());
        assertNull(compound.get("first"));
    }

    @Test
    void iteratorVisitsAllEntriesInInsertionOrder() {
        CompoundTag compound = fivesEntries();
        List<String> keys = new ArrayList<>();
        Iterator<Map.Entry<String, Tag<?>>> iterator = compound.iterator();
        while (iterator.hasNext()) {
            keys.add(iterator.next().getKey());
        }
        assertEquals(List.of("first", "second", "third", "fourth", "fifth"), keys);
    }

    @Test
    void containsTypeFastPathRespectsTagId() {
        CompoundTag compound = fivesEntries();
        assertTrue(compound.containsType("first", TagType.INT));
        assertFalse(compound.containsType("first", TagType.BYTE));
        assertTrue(compound.containsType("second", TagType.BYTE));
        assertFalse(compound.containsType("missing", TagType.INT));
    }

    @Test
    void containsListOfDetectsMatchingListType() {
        CompoundTag compound = new CompoundTag();
        ListTag<IntTag> ints = new ListTag<>(TagType.INT.getId(), 4);
        ints.add(new IntTag(1));
        compound.put("ints", ints);
        compound.put("scalar", new IntTag(99));

        assertTrue(compound.isSmallMode());
        assertTrue(compound.containsListOf("ints", TagType.INT.getId()));
        assertFalse(compound.containsListOf("ints", TagType.BYTE.getId()));
        assertFalse(compound.containsListOf("scalar", TagType.INT.getId()));
        assertFalse(compound.containsListOf("missing", TagType.INT.getId()));
    }

    @Test
    void equalsAndHashCodeAcrossModes() {
        CompoundTag small = fivesEntries();
        CompoundTag also = fivesEntries();
        assertEquals(small, also);
        assertEquals(small.hashCode(), also.hashCode());

        // Force the second compound into map mode and assert equality is preserved.
        Map<String, Tag<?>> live = also.getValue();
        assertNotNull(live);
        assertFalse(also.isSmallMode());
        assertEquals(small, also);
        assertEquals(small.hashCode(), also.hashCode());

        // Hash matches the Map.hashCode() contract (sum of keyHash ^ valueHash).
        Map<String, Tag<?>> reference = new LinkedHashMap<>();
        reference.put("first", new IntTag(1));
        reference.put("second", new ByteTag((byte) 2));
        reference.put("third", new StringTag("three"));
        reference.put("fourth", new IntTag(4));
        reference.put("fifth", new ByteTag((byte) 5));
        assertEquals(reference.hashCode(), small.hashCode(), "Map.hashCode parity");
    }

    @Test
    void containsValueDetectsTagsAndUnderlyingValues() {
        CompoundTag compound = fivesEntries();
        // Tag-typed value match.
        assertTrue(compound.containsValue(new IntTag(1)));
        // Underlying-value match (legacy contract: also checks tag.getValue()).
        assertTrue(compound.containsValue(1));
        assertTrue(compound.containsValue("three"));
        assertFalse(compound.containsValue("not_present"));
    }

    @Test
    void cloneOfSmallStaysSmallAndIsIndependent() {
        CompoundTag compound = fivesEntries();
        CompoundTag clone = compound.clone();
        assertTrue(clone.isSmallMode());
        assertEquals(compound, clone);

        clone.put("first", new IntTag(999));
        assertEquals(new IntTag(1), compound.get("first"), "original must not change");
        assertEquals(new IntTag(999), clone.get("first"));
    }

}
