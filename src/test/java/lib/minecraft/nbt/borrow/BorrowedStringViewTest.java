package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.NbtFactory;
import lib.minecraft.nbt.tags.collection.CompoundTag;
import lib.minecraft.nbt.tags.collection.ListTag;
import lib.minecraft.nbt.tags.primitive.IntTag;
import lib.minecraft.nbt.tags.primitive.StringTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase D3 regression test for {@link BorrowedCompoundTag#getStringView(String)} and
 * {@link BorrowedListTag#getStringView(int)}.
 *
 * <p>The view accessors are the borrow API's zero-allocation key probe: they reuse the same
 * tape walk as {@link BorrowedCompoundTag#get(String)} but stop at the {@link MutfStringView}
 * instead of constructing a {@link BorrowedStringTag} and decoding the payload. The test pins
 * down five things: ASCII fast-path equality without decode, type-mismatch null returns,
 * absent-key null returns, list-index round-trips, and BMP non-ASCII fall-through to the
 * cached decoded form.</p>
 */
class BorrowedStringViewTest {

    @Test
    @DisplayName("getStringView returns a view that equalsString the encoded value")
    void compoundGetStringViewEqualsAscii() throws IOException {
        CompoundTag root = new CompoundTag();
        root.put("id", new StringTag("Hypixel"));
        root.put("display", new StringTag("Diamond Sword"));

        byte[] payload = NbtFactory.toByteArray(root);
        BorrowedCompoundTag borrowed = NbtFactory.borrowFromByteArray(payload);

        MutfStringView idView = borrowed.getStringView("id");
        assertNotNull(idView, "id view must be present");
        assertTrue(idView.equalsString("Hypixel"));
        assertFalse(idView.equalsString("hypixel"), "case mismatch must not match");
        assertFalse(idView.equalsString("Hypixe"), "length mismatch must not match");

        MutfStringView displayView = borrowed.getStringView("display");
        assertNotNull(displayView);
        assertTrue(displayView.equalsString("Diamond Sword"));
    }

    @Test
    @DisplayName("getStringView returns null for absent keys")
    void compoundGetStringViewMissingKeyReturnsNull() throws IOException {
        CompoundTag root = new CompoundTag();
        root.put("id", new StringTag("Hypixel"));

        byte[] payload = NbtFactory.toByteArray(root);
        BorrowedCompoundTag borrowed = NbtFactory.borrowFromByteArray(payload);

        assertNull(borrowed.getStringView("missingKey"));
        assertNull(borrowed.getStringView(""));
    }

    @Test
    @DisplayName("getStringView returns null when the matched entry is not a string")
    void compoundGetStringViewWrongTypeReturnsNull() throws IOException {
        CompoundTag root = new CompoundTag();
        root.put("intKey", IntTag.of(42));
        root.put("compoundKey", new CompoundTag());

        byte[] payload = NbtFactory.toByteArray(root);
        BorrowedCompoundTag borrowed = NbtFactory.borrowFromByteArray(payload);

        assertNull(borrowed.getStringView("intKey"), "int value must yield null");
        assertNull(borrowed.getStringView("compoundKey"), "compound value must yield null");
    }

    @Test
    @DisplayName("BorrowedListTag.getStringView round-trips a list of strings")
    void listGetStringView() throws IOException {
        ListTag<StringTag> lore = new ListTag<>();
        lore.add(new StringTag("first line"));
        lore.add(new StringTag("second line"));
        lore.add(new StringTag("third line"));

        CompoundTag display = new CompoundTag();
        display.put("Lore", lore);
        CompoundTag root = new CompoundTag();
        root.put("display", display);

        byte[] payload = NbtFactory.toByteArray(root);
        BorrowedCompoundTag borrowed = NbtFactory.borrowFromByteArray(payload);

        BorrowedTag<?> displayTag = borrowed.get("display");
        assertTrue(displayTag instanceof BorrowedCompoundTag);

        BorrowedTag<?> loreTag = ((BorrowedCompoundTag) displayTag).get("Lore");
        assertTrue(loreTag instanceof BorrowedListTag);
        BorrowedListTag loreList = (BorrowedListTag) loreTag;

        MutfStringView first = loreList.getStringView(0);
        assertNotNull(first);
        assertEquals("first line", first.toString());

        MutfStringView second = loreList.getStringView(1);
        assertNotNull(second);
        assertTrue(second.equalsString("second line"));

        MutfStringView third = loreList.getStringView(2);
        assertNotNull(third);
        assertEquals("third line", third.toString());
    }

    @Test
    @DisplayName("BorrowedListTag.getStringView returns null for non-string elements")
    void listGetStringViewWrongTypeReturnsNull() throws IOException {
        ListTag<CompoundTag> compounds = new ListTag<>();
        compounds.add(new CompoundTag());
        compounds.add(new CompoundTag());

        CompoundTag root = new CompoundTag();
        root.put("entries", compounds);

        byte[] payload = NbtFactory.toByteArray(root);
        BorrowedCompoundTag borrowed = NbtFactory.borrowFromByteArray(payload);

        BorrowedTag<?> entriesTag = borrowed.get("entries");
        assertTrue(entriesTag instanceof BorrowedListTag);
        BorrowedListTag entries = (BorrowedListTag) entriesTag;

        assertNull(entries.getStringView(0));
        assertNull(entries.getStringView(1));
    }

    @Test
    @DisplayName("BorrowedListTag.getStringView throws on out-of-range indices")
    void listGetStringViewIndexOutOfBounds() throws IOException {
        ListTag<StringTag> lore = new ListTag<>();
        lore.add(new StringTag("only"));

        CompoundTag root = new CompoundTag();
        root.put("Lore", lore);

        byte[] payload = NbtFactory.toByteArray(root);
        BorrowedCompoundTag borrowed = NbtFactory.borrowFromByteArray(payload);

        BorrowedTag<?> loreTag = borrowed.get("Lore");
        assertTrue(loreTag instanceof BorrowedListTag);
        BorrowedListTag loreList = (BorrowedListTag) loreTag;

        assertThrows(IndexOutOfBoundsException.class, () -> loreList.getStringView(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> loreList.getStringView(1));
        assertThrows(IndexOutOfBoundsException.class, () -> loreList.getStringView(99));
    }

    @Test
    @DisplayName("getStringView round-trips BMP non-ASCII payloads through toString()")
    void compoundGetStringViewBmpRoundTrip() throws IOException {
        // Mix of ASCII, BMP non-ASCII (cyrillic), and a supplementary character so the
        // ASCII fast-path is force-skipped and the cached decode path is exercised.
        String value = "Привет 😀";
        CompoundTag root = new CompoundTag();
        root.put("greeting", new StringTag(value));

        byte[] payload = NbtFactory.toByteArray(root);
        BorrowedCompoundTag borrowed = NbtFactory.borrowFromByteArray(payload);

        MutfStringView view = borrowed.getStringView("greeting");
        assertNotNull(view);
        assertFalse(view.isAscii(), "non-ASCII payload must take the slow path");
        assertEquals(value, view.toString());
        assertTrue(view.equalsString(value));
    }

    @Test
    @DisplayName("getStringView's hashCode equals the decoded String's hashCode")
    void compoundGetStringViewHashCodeMatchesDecoded() throws IOException {
        CompoundTag root = new CompoundTag();
        root.put("ascii", new StringTag("Hypixel"));
        root.put("bmp", new StringTag("éclair"));

        byte[] payload = NbtFactory.toByteArray(root);
        BorrowedCompoundTag borrowed = NbtFactory.borrowFromByteArray(payload);

        MutfStringView ascii = borrowed.getStringView("ascii");
        assertNotNull(ascii);
        assertEquals("Hypixel".hashCode(), ascii.hashCode());

        MutfStringView bmp = borrowed.getStringView("bmp");
        assertNotNull(bmp);
        assertEquals("éclair".hashCode(), bmp.hashCode());
    }

    @Test
    @DisplayName("getStringView on an empty string returns a zero-length view")
    void compoundGetStringViewEmptyValue() throws IOException {
        CompoundTag root = new CompoundTag();
        root.put("empty", new StringTag(""));

        byte[] payload = NbtFactory.toByteArray(root);
        BorrowedCompoundTag borrowed = NbtFactory.borrowFromByteArray(payload);

        MutfStringView view = borrowed.getStringView("empty");
        assertNotNull(view);
        assertEquals(0, view.byteLength());
        assertTrue(view.isEmpty());
        assertTrue(view.equalsString(""));
        assertEquals(0, view.hashCode());
    }

}
