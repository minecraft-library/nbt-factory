package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.tags.Tag;
import lib.minecraft.nbt.tags.array.ByteArrayTag;
import lib.minecraft.nbt.tags.array.IntArrayTag;
import lib.minecraft.nbt.tags.array.LongArrayTag;
import lib.minecraft.nbt.tags.collection.CompoundTag;
import lib.minecraft.nbt.tags.collection.ListTag;
import lib.minecraft.nbt.tags.primitive.ByteTag;
import lib.minecraft.nbt.tags.primitive.DoubleTag;
import lib.minecraft.nbt.tags.primitive.FloatTag;
import lib.minecraft.nbt.tags.primitive.IntTag;
import lib.minecraft.nbt.tags.primitive.LongTag;
import lib.minecraft.nbt.tags.primitive.ShortTag;
import lib.minecraft.nbt.tags.primitive.StringTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Targeted coverage for the C3 navigators using hand-built compounds where every key, type, and
 * order is known in advance. {@code BorrowParityTest} pins parity on the simdnbt corpus and the
 * auction fixture; this class exercises the surface-level navigation methods on every tag kind.
 */
class BorrowedNavigatorTest {

    @Test
    @DisplayName("Tape.root() returns a BorrowedCompoundTag at tape index 0")
    void rootReturnsCompoundNavigator() {
        CompoundTag root = new CompoundTag();
        root.put("a", new IntTag(42));

        Tape tape = Tape.encode(root);
        BorrowedCompoundTag borrowed = tape.root();

        assertEquals(1, borrowed.size());
        assertFalse(borrowed.isEmpty());
        assertTrue(borrowed.containsKey("a"));
        assertFalse(borrowed.containsKey("missing"));
    }

    @Test
    @DisplayName("Tape.root() rejects empty tape")
    void rootRejectsEmptyTape() {
        Tape empty = new Tape(new long[0], 0, new byte[0]);
        assertThrows(RuntimeException.class, empty::root);
    }

    @Test
    @DisplayName("All six numeric primitive kinds round trip through borrowed navigators")
    void primitiveKindsRoundTrip() {
        CompoundTag root = new CompoundTag();
        root.put("byte", new ByteTag((byte) -42));
        root.put("short", new ShortTag((short) -32000));
        root.put("int", new IntTag(Integer.MIN_VALUE));
        root.put("long", new LongTag(Long.MIN_VALUE));
        root.put("float", new FloatTag(1.5f));
        root.put("double", new DoubleTag(Math.PI));

        BorrowedCompoundTag borrowed = Tape.encode(root).root();

        BorrowedTag<?> byteTag = borrowed.get("byte");
        assertInstanceOf(BorrowedByteTag.class, byteTag);
        assertEquals((byte) -42, ((BorrowedByteTag) byteTag).getByteValue());

        BorrowedTag<?> shortTag = borrowed.get("short");
        assertInstanceOf(BorrowedShortTag.class, shortTag);
        assertEquals((short) -32000, ((BorrowedShortTag) shortTag).getShortValue());

        BorrowedTag<?> intTag = borrowed.get("int");
        assertInstanceOf(BorrowedIntTag.class, intTag);
        assertEquals(Integer.MIN_VALUE, ((BorrowedIntTag) intTag).getIntValue());

        BorrowedTag<?> longTag = borrowed.get("long");
        assertInstanceOf(BorrowedLongTag.class, longTag);
        assertEquals(Long.MIN_VALUE, ((BorrowedLongTag) longTag).getLongValue());

        BorrowedTag<?> floatTag = borrowed.get("float");
        assertInstanceOf(BorrowedFloatTag.class, floatTag);
        assertEquals(1.5f, ((BorrowedFloatTag) floatTag).getFloatValue());

        BorrowedTag<?> doubleTag = borrowed.get("double");
        assertInstanceOf(BorrowedDoubleTag.class, doubleTag);
        assertEquals(Math.PI, ((BorrowedDoubleTag) doubleTag).getDoubleValue());
    }

    @Test
    @DisplayName("BorrowedStringTag caches the decoded value across getValue() calls")
    void stringTagCachesDecodedValue() {
        CompoundTag root = new CompoundTag();
        root.put("k", new StringTag("hello world"));

        BorrowedCompoundTag borrowed = Tape.encode(root).root();
        BorrowedTag<?> tag = borrowed.get("k");
        assertInstanceOf(BorrowedStringTag.class, tag);

        BorrowedStringTag stringTag = (BorrowedStringTag) tag;
        String first = stringTag.getValue();
        String second = stringTag.getValue();

        assertEquals("hello world", first);
        // Cached - same reference on the second call.
        assertSame(first, second);
    }

    @Test
    @DisplayName("All three array kinds expose RawList views and materialize correctly")
    void arrayKindsExposeRawListsAndMaterialize() {
        byte[] bytes = {1, 2, 3, -4, -5};
        int[] ints = {Integer.MIN_VALUE, 0, Integer.MAX_VALUE, -1, 7};
        long[] longs = {Long.MIN_VALUE, 0L, Long.MAX_VALUE, -1L, 9001L};

        CompoundTag root = new CompoundTag();
        root.put("bytes", new ByteArrayTag(bytes));
        root.put("ints", new IntArrayTag(ints));
        root.put("longs", new LongArrayTag(longs));

        BorrowedCompoundTag borrowed = Tape.encode(root).root();

        BorrowedByteArrayTag byteArrayTag = (BorrowedByteArrayTag) borrowed.get("bytes");
        assertNotNull(byteArrayTag);
        assertEquals(bytes.length, byteArrayTag.size());
        assertArrayEquals(bytes, byteArrayTag.toByteArray());
        RawList byteList = byteArrayTag.rawList();
        assertEquals(TapeKind.BYTE_ARRAY_PTR, byteList.elementKind());
        for (int i = 0; i < bytes.length; i++)
            assertEquals(bytes[i], byteList.getByte(i));

        BorrowedIntArrayTag intArrayTag = (BorrowedIntArrayTag) borrowed.get("ints");
        assertNotNull(intArrayTag);
        assertEquals(ints.length, intArrayTag.size());
        assertArrayEquals(ints, intArrayTag.toIntArray());
        RawList intList = intArrayTag.rawList();
        for (int i = 0; i < ints.length; i++)
            assertEquals(ints[i], intList.getInt(i));

        BorrowedLongArrayTag longArrayTag = (BorrowedLongArrayTag) borrowed.get("longs");
        assertNotNull(longArrayTag);
        assertEquals(longs.length, longArrayTag.size());
        assertArrayEquals(longs, longArrayTag.toLongArray());
        RawList longList = longArrayTag.rawList();
        for (int i = 0; i < longs.length; i++)
            assertEquals(longs[i], longList.getLong(i));
    }

    @Test
    @DisplayName("BorrowedListTag exposes elementId, size, get(i), and iterator in insertion order")
    void listTagNavigation() {
        ListTag<IntTag> list = new ListTag<>();
        list.add(new IntTag(10));
        list.add(new IntTag(20));
        list.add(new IntTag(30));

        CompoundTag root = new CompoundTag();
        root.put("list", list);

        BorrowedCompoundTag borrowed = Tape.encode(root).root();
        BorrowedListTag borrowedList = (BorrowedListTag) borrowed.get("list");
        assertNotNull(borrowedList);

        // Wire elementId for IntTag is TagType.INT.id == 3.
        assertEquals((byte) 3, borrowedList.getElementId());
        assertEquals(3, borrowedList.size());
        assertFalse(borrowedList.isEmpty());

        assertEquals(10, ((BorrowedIntTag) borrowedList.get(0)).getIntValue());
        assertEquals(20, ((BorrowedIntTag) borrowedList.get(1)).getIntValue());
        assertEquals(30, ((BorrowedIntTag) borrowedList.get(2)).getIntValue());
        assertThrows(IndexOutOfBoundsException.class, () -> borrowedList.get(3));
        assertThrows(IndexOutOfBoundsException.class, () -> borrowedList.get(-1));

        // Iterator walks in insertion order.
        Iterator<BorrowedTag<?>> it = borrowedList.iterator();
        assertEquals(10, ((BorrowedIntTag) it.next()).getIntValue());
        assertEquals(20, ((BorrowedIntTag) it.next()).getIntValue());
        assertEquals(30, ((BorrowedIntTag) it.next()).getIntValue());
        assertFalse(it.hasNext());
    }

    @Test
    @DisplayName("BorrowedCompoundTag.entries() yields keys in insertion order")
    void compoundEntriesPreserveInsertionOrder() {
        CompoundTag root = new CompoundTag();
        root.put("zeta", new IntTag(1));
        root.put("alpha", new IntTag(2));
        root.put("middle", new IntTag(3));

        BorrowedCompoundTag borrowed = Tape.encode(root).root();
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, BorrowedTag<?>> entry : borrowed.entries()) {
            keys.add(entry.getKey());
            // Exercise the value navigator while iterating.
            assertInstanceOf(BorrowedIntTag.class, entry.getValue());
        }

        assertEquals(List.of("zeta", "alpha", "middle"), keys);
    }

    @Test
    @DisplayName("BorrowedCompoundTag.get returns null for missing keys")
    void compoundGetMissingReturnsNull() {
        CompoundTag root = new CompoundTag();
        root.put("present", new IntTag(1));

        BorrowedCompoundTag borrowed = Tape.encode(root).root();
        assertNull(borrowed.get("missing"));
        assertNotNull(borrowed.get("present"));
    }

    @Test
    @DisplayName("Empty list preserves wire elementId through borrow + materialize")
    void emptyListPreservesElementId() {
        // ListTag exposes a constructor that pre-seeds elementId for empty lists.
        ListTag<ByteTag> emptyByteList = new ListTag<>((byte) 1, 0);
        CompoundTag root = new CompoundTag();
        root.put("empty", emptyByteList);

        BorrowedCompoundTag borrowed = Tape.encode(root).root();
        BorrowedListTag borrowedList = (BorrowedListTag) borrowed.get("empty");
        assertNotNull(borrowedList);
        assertEquals((byte) 1, borrowedList.getElementId());
        assertEquals(0, borrowedList.size());
        assertTrue(borrowedList.isEmpty());

        // materialize() preserves the elementId on round trip.
        ListTag<?> materialized = borrowedList.materialize();
        assertEquals((byte) 1, materialized.getListType());
    }

    @Test
    @DisplayName("Nested compound navigation reaches inner values")
    void nestedCompoundNavigation() {
        CompoundTag inner = new CompoundTag();
        inner.put("c", new IntTag(42));
        CompoundTag mid = new CompoundTag();
        mid.put("b", inner);
        CompoundTag root = new CompoundTag();
        root.put("a", mid);

        BorrowedCompoundTag borrowed = Tape.encode(root).root();
        BorrowedCompoundTag a = (BorrowedCompoundTag) borrowed.get("a");
        assertNotNull(a);
        BorrowedCompoundTag b = (BorrowedCompoundTag) a.get("b");
        assertNotNull(b);
        BorrowedTag<?> c = b.get("c");
        assertEquals(42, ((BorrowedIntTag) c).getIntValue());
    }

    @Test
    @DisplayName("getId() byte matches the equivalent owned tag for every kind")
    void getIdMatchesOwnedTag() {
        CompoundTag root = new CompoundTag();
        root.put("byte", new ByteTag((byte) 1));
        root.put("short", new ShortTag((short) 1));
        root.put("int", new IntTag(1));
        root.put("long", new LongTag(1L));
        root.put("float", new FloatTag(1f));
        root.put("double", new DoubleTag(1d));
        root.put("string", new StringTag("x"));
        root.put("ba", new ByteArrayTag(new byte[]{1}));
        root.put("ia", new IntArrayTag(new int[]{1}));
        root.put("la", new LongArrayTag(new long[]{1L}));
        root.put("list", new ListTag<>((byte) 3, 0));
        root.put("compound", new CompoundTag());

        BorrowedCompoundTag borrowed = Tape.encode(root).root();
        for (Map.Entry<String, Tag<?>> entry : root.entrySet()) {
            BorrowedTag<?> view = borrowed.get(entry.getKey());
            assertNotNull(view, "missing key " + entry.getKey());
            assertEquals(entry.getValue().getId(), view.getId(),
                "id mismatch for key " + entry.getKey());
        }
    }

}
