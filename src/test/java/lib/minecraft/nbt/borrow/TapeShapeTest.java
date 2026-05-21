package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.tags.collection.CompoundTag;
import lib.minecraft.nbt.tags.collection.ListTag;
import lib.minecraft.nbt.tags.primitive.IntTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Pins the bit-packing layout and tape-element-count formula for hand-built fixtures.
 *
 * <p>The element-count formula encoded here is:</p>
 * <ul>
 *   <li>Each compound entry contributes {@code 1 (KEY_PTR) + valueElementCount} tape slots.</li>
 *   <li>Each compound contributes {@code 1 (HEADER) + sum(entryElements) + 1 (END)} slots.</li>
 *   <li>Each list contributes {@code 1 (HEADER) + sum(elementElements) + 1 (END)} slots.</li>
 *   <li>Each non-container value contributes {@code 1} slot.</li>
 * </ul>
 *
 * <p>So {@code {a: 42}} (one int entry) is 4 slots: HEADER, KEY_PTR, INT_INLINE, END.
 * {@code {a: {b: {c: 42}}}} is 10 slots. {@code {outer: [[1,2],[3]]}} is 12 slots.</p>
 *
 * <p>C2's streaming parser must produce identical shapes for the same inputs.</p>
 */
class TapeShapeTest {

    @Test
    @DisplayName("single-int compound: 4 tape slots in HEADER, KEY_PTR, INT_INLINE, END order")
    void singleIntCompoundShape() {
        CompoundTag root = new CompoundTag();
        root.put("a", new IntTag(42));

        Tape tape = Tape.encode(root);
        assertEquals(4, tape.tapeSize(), "single-int compound should produce exactly 4 tape slots");

        assertEquals(TapeKind.COMPOUND_HEADER, TapeElement.unpackKind(tape.elementAt(0)));
        assertEquals(TapeKind.KEY_PTR, TapeElement.unpackKind(tape.elementAt(1)));
        assertEquals(TapeKind.INT_INLINE, TapeElement.unpackKind(tape.elementAt(2)));
        assertEquals(TapeKind.COMPOUND_END, TapeElement.unpackKind(tape.elementAt(3)));

        // Header points at its matching end.
        long header = tape.elementAt(0);
        assertEquals(3, TapeElement.unpackEndOffset(header), "root COMPOUND_HEADER endOffset must point at the matching END");
        assertEquals(1, TapeElement.unpackApproxLen(header), "approxLen reflects entry count (1)");

        // Inline value survives the round trip through the 56-bit payload.
        assertEquals(42, (int) TapeElement.unpackValue(tape.elementAt(2)));

        // END's back-reference points at the originating header.
        long end = tape.elementAt(3);
        assertEquals(0, TapeElement.unpackEndOffset(end), "COMPOUND_END low 32 bits back-reference the header index");
    }

    @Test
    @DisplayName("3-level nested compound: 10 tape slots; inner endOffsets nest correctly")
    void threeLevelNestedCompoundShape() {
        // {a: {b: {c: 42}}}
        CompoundTag inner = new CompoundTag();
        inner.put("c", new IntTag(42));
        CompoundTag mid = new CompoundTag();
        mid.put("b", inner);
        CompoundTag root = new CompoundTag();
        root.put("a", mid);

        Tape tape = Tape.encode(root);
        assertEquals(10, tape.tapeSize(), "3-level nested compound should produce exactly 10 tape slots");

        // Layout: [0]ROOT_HEADER [1]KEY"a" [2]MID_HEADER [3]KEY"b" [4]INNER_HEADER
        //         [5]KEY"c" [6]INT(42) [7]INNER_END [8]MID_END [9]ROOT_END
        assertEquals(TapeKind.COMPOUND_HEADER, TapeElement.unpackKind(tape.elementAt(0)));
        assertEquals(TapeKind.COMPOUND_HEADER, TapeElement.unpackKind(tape.elementAt(2)));
        assertEquals(TapeKind.COMPOUND_HEADER, TapeElement.unpackKind(tape.elementAt(4)));
        assertEquals(TapeKind.INT_INLINE, TapeElement.unpackKind(tape.elementAt(6)));
        assertEquals(TapeKind.COMPOUND_END, TapeElement.unpackKind(tape.elementAt(7)));
        assertEquals(TapeKind.COMPOUND_END, TapeElement.unpackKind(tape.elementAt(8)));
        assertEquals(TapeKind.COMPOUND_END, TapeElement.unpackKind(tape.elementAt(9)));

        // Header endOffsets nest correctly.
        assertEquals(9, TapeElement.unpackEndOffset(tape.elementAt(0)), "root header end at 9");
        assertEquals(8, TapeElement.unpackEndOffset(tape.elementAt(2)), "mid header end at 8");
        assertEquals(7, TapeElement.unpackEndOffset(tape.elementAt(4)), "inner header end at 7");

        // findChildTapeIndex walks the right level using the header's endOffset.
        int aValueIdx = tape.findChildTapeIndex(0, "a");
        assertEquals(2, aValueIdx, "key 'a' at root resolves to mid header at tape[2]");

        int bValueIdx = tape.findChildTapeIndex(aValueIdx, "b");
        assertEquals(4, bValueIdx, "key 'b' under mid resolves to inner header at tape[4]");

        int cValueIdx = tape.findChildTapeIndex(bValueIdx, "c");
        assertEquals(6, cValueIdx, "key 'c' under inner resolves to INT at tape[6]");

        // Negative lookups stay bounded to the matching subtree.
        assertEquals(Tape.NOT_FOUND, tape.findChildTapeIndex(0, "missing"));
    }

    @Test
    @DisplayName("list-of-lists: 12 tape slots; list endOffsets nest correctly")
    void listOfListsShape() {
        // {outer: [[1,2], [3]]}
        ListTag<IntTag> inner1 = new ListTag<>();
        inner1.add(new IntTag(1));
        inner1.add(new IntTag(2));

        ListTag<IntTag> inner2 = new ListTag<>();
        inner2.add(new IntTag(3));

        ListTag<ListTag<IntTag>> outer = new ListTag<>();
        outer.add(inner1);
        outer.add(inner2);

        CompoundTag root = new CompoundTag();
        root.put("outer", outer);

        Tape tape = Tape.encode(root);
        assertEquals(12, tape.tapeSize(), "list-of-lists should produce exactly 12 tape slots");

        // Layout: [0]ROOT_HEADER [1]KEY [2]OUTER_LIST [3]INNER1_LIST [4]INT [5]INT
        //         [6]INNER1_END [7]INNER2_LIST [8]INT [9]INNER2_END [10]OUTER_END [11]ROOT_END
        assertEquals(TapeKind.LIST_HEADER, TapeElement.unpackKind(tape.elementAt(2)));
        assertEquals(TapeKind.LIST_HEADER, TapeElement.unpackKind(tape.elementAt(3)));
        assertEquals(TapeKind.LIST_END, TapeElement.unpackKind(tape.elementAt(6)));
        assertEquals(TapeKind.LIST_HEADER, TapeElement.unpackKind(tape.elementAt(7)));
        assertEquals(TapeKind.LIST_END, TapeElement.unpackKind(tape.elementAt(9)));
        assertEquals(TapeKind.LIST_END, TapeElement.unpackKind(tape.elementAt(10)));
        assertEquals(TapeKind.COMPOUND_END, TapeElement.unpackKind(tape.elementAt(11)));

        // Outer list approxLen mirrors element count; endOffset points at its matching LIST_END.
        long outerHeader = tape.elementAt(2);
        assertEquals(2, TapeElement.unpackApproxLen(outerHeader));
        assertEquals(10, TapeElement.unpackEndOffset(outerHeader));

        // Inner1 / inner2 endOffsets do not collide with the outer's.
        assertEquals(6, TapeElement.unpackEndOffset(tape.elementAt(3)));
        assertEquals(9, TapeElement.unpackEndOffset(tape.elementAt(7)));

        // nextSibling skips the entire inner1 subtree from the outer-list view.
        assertEquals(7, tape.nextSibling(3), "nextSibling skips inner1 subtree from inner1 header to inner2 header");

        // Sanity: a non-container value's nextSibling is just +1.
        assertEquals(5, tape.nextSibling(4));
    }

    @Test
    @DisplayName("TapeElement.pack inline byte preserves signed value via 56-bit sign extension")
    void inlineByteSignedRoundTrip() {
        long packed = TapeElement.pack(TapeKind.BYTE_INLINE, (byte) -42);
        assertEquals(TapeKind.BYTE_INLINE, TapeElement.unpackKind(packed));
        assertEquals(-42, (byte) TapeElement.unpackValue(packed));

        long packedShort = TapeElement.pack(TapeKind.SHORT_INLINE, (short) -1);
        assertEquals((short) -1, (short) TapeElement.unpackValue(packedShort));

        long packedInt = TapeElement.pack(TapeKind.INT_INLINE, Integer.MIN_VALUE);
        assertEquals(Integer.MIN_VALUE, (int) TapeElement.unpackValue(packedInt));
    }

    @Test
    @DisplayName("packCompoundHeader saturates approxLen at 0xFFFFFF")
    void approxLenSaturates() {
        long packed = TapeElement.packCompoundHeader(TapeElement.MAX_APPROX_LEN + 100, 999);
        assertEquals(TapeElement.MAX_APPROX_LEN, TapeElement.unpackApproxLen(packed));
        assertEquals(999, TapeElement.unpackEndOffset(packed));

        // The kind is preserved across saturation.
        assertEquals(TapeKind.COMPOUND_HEADER, TapeElement.unpackKind(packed));

        // List header packs differently from compound header (different ordinals) and stashes
        // the wire element-id in its 8-bit reserved slot.
        long listPacked = TapeElement.packListHeader((byte) 6, 5, 999);
        assertEquals(TapeKind.LIST_HEADER, TapeElement.unpackKind(listPacked));
        assertEquals((byte) 6, TapeElement.unpackListElementId(listPacked));
        assertEquals(5, TapeElement.unpackApproxLen(listPacked));
        assertEquals(999, TapeElement.unpackEndOffset(listPacked));
        assertNotEquals(packed, listPacked);
    }

}
