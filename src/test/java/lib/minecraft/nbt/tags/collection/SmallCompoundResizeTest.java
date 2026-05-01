package lib.minecraft.nbt.tags.collection;

import lib.minecraft.nbt.tags.Tag;
import lib.minecraft.nbt.tags.primitive.IntTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the small-mode -> map-mode promotion contract:
 * <ul>
 *   <li>Promotion fires on the 17th distinct insertion, not before.</li>
 *   <li>{@link CompoundTag#getValue()} forces eager promotion regardless of size.</li>
 *   <li>Promoted compounds preserve insertion order and continue to handle large workloads.</li>
 *   <li>Promotion is one-way - {@link CompoundTag#clear()} after promotion stays in map mode.</li>
 * </ul>
 */
class SmallCompoundResizeTest {

    @Test
    void sixteenInsertionsStayInSmallMode() {
        CompoundTag compound = new CompoundTag();
        for (int i = 0; i < 16; i++)
            compound.put("k" + i, new IntTag(i));

        assertTrue(compound.isSmallMode(), "16 entries should remain in small mode");
        assertEquals(16, compound.size());
    }

    @Test
    void seventeenthInsertionTriggersPromotion() {
        CompoundTag compound = new CompoundTag();
        for (int i = 0; i < 16; i++)
            compound.put("k" + i, new IntTag(i));
        assertTrue(compound.isSmallMode());

        compound.put("k16", new IntTag(16));
        assertFalse(compound.isSmallMode(), "17th entry must promote to map mode");
        assertEquals(17, compound.size());

        // Every original entry survives in insertion order.
        List<String> seenKeys = new ArrayList<>();
        for (Map.Entry<String, Tag<?>> entry : compound)
            seenKeys.add(entry.getKey());

        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 17; i++)
            expected.add("k" + i);
        assertEquals(expected, seenKeys);

        // Values match.
        for (int i = 0; i < 17; i++)
            assertEquals(new IntTag(i), compound.get("k" + i));
    }

    @Test
    void overwriteAtSixteenStaysSmall() {
        CompoundTag compound = new CompoundTag();
        for (int i = 0; i < 16; i++)
            compound.put("k" + i, new IntTag(i));
        assertTrue(compound.isSmallMode());

        // Overwriting an existing key does not consume a new slot.
        Tag<?> previous = compound.put("k0", new IntTag(999));
        assertEquals(new IntTag(0), previous);
        assertTrue(compound.isSmallMode(), "overwrite of an existing key must not promote");
        assertEquals(16, compound.size());
        assertEquals(new IntTag(999), compound.get("k0"));
    }

    @Test
    void postPromotionInsertions() {
        CompoundTag compound = new CompoundTag();
        for (int i = 0; i < 50; i++)
            compound.put("k" + i, new IntTag(i));

        assertFalse(compound.isSmallMode());
        assertEquals(50, compound.size());
        for (int i = 0; i < 50; i++)
            assertEquals(new IntTag(i), compound.get("k" + i));
    }

    @Test
    void getValueForcesPromotion() {
        CompoundTag compound = new CompoundTag();
        compound.put("a", new IntTag(1));
        compound.put("b", new IntTag(2));
        assertTrue(compound.isSmallMode());

        Map<String, Tag<?>> live = compound.getValue();
        assertNotNull(live);
        assertFalse(compound.isSmallMode(), "getValue() must trigger eager promotion");
        assertEquals(2, live.size());
        assertEquals(new IntTag(1), live.get("a"));

        // Subsequent put() goes through the map and the live reference sees it.
        compound.put("c", new IntTag(3));
        assertEquals(new IntTag(3), live.get("c"));
        assertEquals(3, compound.size());
    }

    @Test
    void getValueOnEmptyCompoundPromotes() {
        CompoundTag compound = new CompoundTag();
        assertTrue(compound.isSmallMode());

        Map<String, Tag<?>> live = compound.getValue();
        assertNotNull(live);
        assertTrue(live.isEmpty());
        assertFalse(compound.isSmallMode());
    }

    @Test
    void clearAfterPromotionStaysInMapMode() {
        CompoundTag compound = new CompoundTag();
        for (int i = 0; i < 20; i++)
            compound.put("k" + i, new IntTag(i));
        assertFalse(compound.isSmallMode());

        compound.clear();
        assertEquals(0, compound.size());
        assertFalse(compound.isSmallMode(), "promotion is one-way: clear must not demote");
    }

    @Test
    void sizedConstructorAboveThresholdSkipsSmallMode() {
        CompoundTag compound = new CompoundTag(64);
        assertFalse(compound.isSmallMode());
        for (int i = 0; i < 50; i++)
            compound.put("k" + i, new IntTag(i));
        assertEquals(50, compound.size());
    }

    @Test
    void promotionPreservesOriginalEntriesInsertionOrderWithRemoves() {
        CompoundTag compound = new CompoundTag();
        for (int i = 0; i < 10; i++)
            compound.put("k" + i, new IntTag(i));
        compound.remove("k3");
        compound.remove("k7");
        assertTrue(compound.isSmallMode());
        assertEquals(8, compound.size());

        // Now grow past the threshold to trigger promotion. Remaining + 9 new = 17 distinct keys.
        for (int i = 10; i < 19; i++)
            compound.put("k" + i, new IntTag(i));
        assertFalse(compound.isSmallMode());
        assertEquals(17, compound.size());

        // Insertion order: 0,1,2,4,5,6,8,9,10,11,...,18
        List<String> seenKeys = new ArrayList<>();
        for (Map.Entry<String, Tag<?>> entry : compound)
            seenKeys.add(entry.getKey());

        List<String> expected = List.of(
            "k0", "k1", "k2", "k4", "k5", "k6", "k8", "k9",
            "k10", "k11", "k12", "k13", "k14", "k15", "k16", "k17", "k18"
        );
        assertEquals(expected, seenKeys);
        assertNull(compound.get("k3"));
        assertNull(compound.get("k7"));
    }

}
