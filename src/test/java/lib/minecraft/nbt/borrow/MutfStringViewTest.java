package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.io.NbtModifiedUtf8;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks in the load-bearing C4 invariant: {@link MutfStringView#hashCode()} matches
 * {@link String#hashCode()} byte-for-byte across the full ASCII / BMP / supplementary /
 * {@code C0 80} parity matrix, and {@link MutfStringView#equalsString(String)} matches
 * {@link String#equals(Object)} for every input.
 *
 * <p>The fast path bytes-vs-{@code char} compare in {@code equalsString} only fires for ASCII
 * payloads where byte count equals character count - tests 5 (BMP), 6 (supplementary), and 7
 * ({@code C0 80}) pin the non-ASCII fall-through to {@code toString().equals(...)} and confirm
 * the byte-level shortcut is not erroneously taken.</p>
 */
@DisplayName("MutfStringView - parity matrix vs String.hashCode / String.equals")
class MutfStringViewTest {

    /**
     * Encodes {@code value} via the production {@link NbtModifiedUtf8} encoder and wraps the
     * result in a {@link MutfStringView}. Asserts the encoder produces a stable byte sequence so
     * the test does not depend on a hand-coded encoder duplicating production logic.
     */
    private static MutfStringView encodedView(String value) {
        int len = NbtModifiedUtf8.encodedLength(value);
        byte[] buf = new byte[len];
        int written = NbtModifiedUtf8.encode(value, buf, 0);
        assertEquals(len, written, "encoder writes the predicted byte count");
        return new MutfStringView(buf, 0, len);
    }

    @Test
    @DisplayName("ASCII 'hello' - decode, hashCode, equalsString all match String")
    void asciiHelloRoundTrip() {
        MutfStringView view = encodedView("hello");

        assertEquals(5, view.byteLength());
        assertFalse(view.isEmpty());
        assertTrue(view.isAscii());
        assertEquals("hello", view.toString());
        assertEquals("hello".hashCode(), view.hashCode());
        assertTrue(view.equalsString("hello"));
        assertFalse(view.equalsString("hi"));
        assertFalse(view.equalsString("hello!"));
        assertFalse(view.equalsString(null));
    }

    @Test
    @DisplayName("ASCII at exactly 8 bytes (chunk boundary) - hash + equality")
    void asciiAtChunkBoundary() {
        // Exactly one 8-byte chunk - probes the body loop with no peel-tail iterations.
        String s = "abcdefgh";
        MutfStringView view = encodedView(s);

        assertEquals(8, view.byteLength());
        assertTrue(view.isAscii());
        assertEquals(s, view.toString());
        assertEquals(s.hashCode(), view.hashCode());
        assertTrue(view.equalsString(s));
    }

    @ParameterizedTest
    @ValueSource(ints = {7, 9})
    @DisplayName("ASCII at 7 / 9 bytes - probes peel paths around the chunk boundary")
    void asciiAtPeelLengths(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++)
            sb.append((char) ('a' + (i % 26)));

        String s = sb.toString();
        MutfStringView view = encodedView(s);

        assertEquals(len, view.byteLength());
        assertTrue(view.isAscii());
        assertEquals(s, view.toString());
        assertEquals(s.hashCode(), view.hashCode());
        assertTrue(view.equalsString(s));
    }

    @Test
    @DisplayName("Empty string - byteLength 0, isEmpty, hashCode 0, equalsString empty")
    void emptyString() {
        MutfStringView view = encodedView("");

        assertEquals(0, view.byteLength());
        assertTrue(view.isEmpty());
        assertTrue(view.isAscii());
        assertEquals("", view.toString());
        assertEquals(0, view.hashCode());
        assertEquals("".hashCode(), view.hashCode());
        assertTrue(view.equalsString(""));
        assertFalse(view.equalsString("x"));
    }

    @Test
    @DisplayName("BMP non-ASCII 'eclair' (with U+00E9) - decode + hash parity through slow path")
    void bmpNonAscii() {
        // The 'e-acute' is U+00E9, encoded as 0xC3 0xA9 in modified UTF-8 (2 bytes).
        String s = "éclair";
        MutfStringView view = encodedView(s);

        // 6 chars but 7 bytes (one 2-byte sequence).
        assertEquals(7, view.byteLength());
        assertFalse(view.isAscii(), "0xC3 has high bit set, so probe rejects ASCII");
        assertEquals(s, view.toString());
        assertEquals(s.hashCode(), view.hashCode());
        assertTrue(view.equalsString(s));
        assertFalse(view.equalsString("eclair"));
    }

    @Test
    @DisplayName("Supplementary code point (emoji) - 6-byte modified-UTF-8 surrogate pair round-trip")
    void supplementaryCodePoint() {
        // U+1F600 'GRINNING FACE' - encoded as a UTF-16 surrogate pair, then each surrogate goes
        // through the 3-byte modified-UTF-8 form: 6 bytes total.
        String s = new String(Character.toChars(0x1F600));
        MutfStringView view = encodedView(s);

        assertEquals(6, view.byteLength());
        assertFalse(view.isAscii());
        assertEquals(s, view.toString());
        assertEquals(s.hashCode(), view.hashCode());
        assertTrue(view.equalsString(s));
    }

    @Test
    @DisplayName("U+0000 as C0 80 - encoded length 2, decoded length 1, hash matches String.hashCode")
    void embeddedNullCharacter() {
        // The single-character string "\0" - encoded modified-UTF-8 byte sequence is 0xC0 0x80.
        MutfStringView view = encodedView("\0");

        assertEquals(2, view.byteLength(), "C0 80 form, not the raw 0x00 byte");
        // 0xC0 has the high bit set so the ASCII probe rejects this case - critical for hash
        // parity, since byte-level hash of (C0, 80) would be 31*0xC0 + 0x80 = 6080, not 0.
        assertFalse(view.isAscii());

        assertEquals("\0", view.toString());
        assertEquals(1, view.toString().length());

        assertEquals(0, "\0".hashCode(), "sanity: String.hashCode(\"\\0\") == 0");
        assertEquals(0, view.hashCode(), "view.hashCode() must equal String.hashCode() for \"\\0\"");
        assertTrue(view.equalsString("\0"));
        assertFalse(view.equalsString(""));
    }

    @Test
    @DisplayName("View vs view byte-equality")
    void viewVsViewByteEquality() {
        MutfStringView a = encodedView("hello");
        MutfStringView b = encodedView("hello");
        MutfStringView c = encodedView("world");
        MutfStringView empty = encodedView("");

        // Same payload, different backing arrays - byte-level equal.
        assertTrue(a.equals(b));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        // Different payloads.
        assertFalse(a.equals(c));
        assertNotEquals(a, c);

        // Different lengths.
        assertFalse(a.equals(empty));

        // Self-equality.
        assertTrue(a.equals(a));

        // null -> false (overload), Object.equals(null) -> false.
        assertFalse(a.equals((MutfStringView) null));
        assertFalse(a.equals((Object) null));

        // Object.equals never matches a String argument.
        assertNotEquals("hello", a);
    }

    @Test
    @DisplayName("View vs view differing only in last byte returns false")
    void viewVsViewLastByteDiffers() {
        MutfStringView a = encodedView("foobarx");
        MutfStringView b = encodedView("foobary");

        assertEquals(a.byteLength(), b.byteLength());
        assertFalse(a.equals(b));
    }

    @Test
    @DisplayName("toString caches the decoded value across calls")
    void toStringCachesResult() {
        MutfStringView view = encodedView("cacheme");
        String first = view.toString();
        String second = view.toString();
        assertSame(first, second, "toString returns the same reference on the second call");

        // asString is an alias - same cached instance.
        assertSame(first, view.asString());
    }

    @Test
    @DisplayName("isAscii caches its result so the probe runs at most once")
    void isAsciiCaches() {
        MutfStringView view = encodedView("ascii");
        // Two reads must produce the same result; second one hits the cache. Correctness is
        // easier to assert than the cache itself, but two calls without exceptions exercises
        // both branches of the asciiState ternary.
        assertTrue(view.isAscii());
        assertTrue(view.isAscii());

        MutfStringView nonAscii = encodedView("é");
        assertFalse(nonAscii.isAscii());
        assertFalse(nonAscii.isAscii());
    }

    @Test
    @DisplayName("Property: 1000 random ASCII strings - hashCode and equalsString match String exactly")
    void propertyAsciiHashAndEquals() {
        Random random = new Random(0xA5C11);

        for (int trial = 0; trial < 1000; trial++) {
            int len = random.nextInt(40);
            char[] chars = new char[len];

            for (int i = 0; i < len; i++)
                chars[i] = (char) (0x20 + random.nextInt(0x60)); // printable ASCII [0x20..0x7F]

            String s = new String(chars);
            MutfStringView view = encodedView(s);

            assertTrue(view.isAscii(), "trial " + trial + ": ASCII probe should accept printable ASCII");
            assertEquals(s, view.toString(), "trial " + trial + ": decode mismatch");
            assertEquals(s.hashCode(), view.hashCode(), "trial " + trial + ": hash mismatch for '" + s + "'");
            assertTrue(view.equalsString(s), "trial " + trial + ": equalsString self should be true");

            // Mismatched-by-suffix probe.
            assertFalse(view.equalsString(s + 'z'), "trial " + trial + ": longer string compares unequal");
        }
    }

    @Test
    @DisplayName("Property: 1000 random BMP-mixed strings - hashCode and equalsString match String exactly")
    void propertyBmpMixedHashAndEquals() {
        Random random = new Random(0xB37C0DE);

        for (int trial = 0; trial < 1000; trial++) {
            int len = random.nextInt(40);
            StringBuilder sb = new StringBuilder(len);

            for (int i = 0; i < len; i++) {
                // Mix ASCII, 2-byte BMP, and 3-byte BMP code points. Avoid surrogate halves so
                // we don't generate invalid UTF-16 sequences from random.nextInt directly.
                int branch = random.nextInt(3);
                int codePoint = switch (branch) {
                    case 0 -> 0x20 + random.nextInt(0x60); // printable ASCII
                    case 1 -> 0x80 + random.nextInt(0x780); // 2-byte BMP [0x80..0x7FF]
                    default -> {
                        // 3-byte BMP [0x800..0xFFFF] minus surrogate pair range [0xD800..0xDFFF].
                        int v = 0x800 + random.nextInt(0xF800);
                        if (v >= 0xD800 && v <= 0xDFFF)
                            v = 0x4E00; // CJK plane fallback
                        yield v;
                    }
                };
                sb.append((char) codePoint);
            }

            String s = sb.toString();
            MutfStringView view = encodedView(s);

            assertEquals(s, view.toString(), "trial " + trial + ": decode mismatch");
            assertEquals(s.hashCode(), view.hashCode(),
                "trial " + trial + ": hash mismatch for length " + s.length());
            assertTrue(view.equalsString(s), "trial " + trial + ": equalsString self should be true");
        }
    }

    @Test
    @DisplayName("fromTagOffset reads the 2-byte length prefix and points at the payload")
    void fromTagOffsetSkipsLengthPrefix() {
        // Hand-build: 2-byte big-endian length + 5 ASCII payload bytes + a sentinel after.
        byte[] buf = {0x00, 0x05, 'a', 'b', 'c', 'd', 'e', 0x7F};
        MutfStringView view = MutfStringView.fromTagOffset(buf, 0);

        assertEquals(5, view.byteLength());
        assertEquals("abcde", view.toString());
        assertEquals("abcde".hashCode(), view.hashCode());
        assertTrue(view.equalsString("abcde"));
    }

    @Test
    @DisplayName("Constructor rejects negative offset / length and out-of-range slice")
    void constructorRejectsBadInput() {
        byte[] buf = new byte[8];
        assertThrows(IllegalArgumentException.class, () -> new MutfStringView(buf, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new MutfStringView(buf, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new MutfStringView(buf, 0, 9));
        assertThrows(IllegalArgumentException.class, () -> new MutfStringView(buf, 5, 4));
    }

    @Test
    @DisplayName("BorrowedStringTag exposes the backing view")
    void borrowedStringTagExposesView() {
        // Indirectly via Tape.encode -> tape.root() -> borrowed.get(...).
        lib.minecraft.nbt.tags.collection.CompoundTag root = new lib.minecraft.nbt.tags.collection.CompoundTag();
        root.put("k", new lib.minecraft.nbt.tags.primitive.StringTag("hello"));

        BorrowedCompoundTag borrowed = Tape.encode(root).root();
        BorrowedStringTag tag = (BorrowedStringTag) borrowed.get("k");
        assertNotNull(tag);

        MutfStringView view = tag.view();
        assertEquals(5, view.byteLength());
        assertEquals("hello", view.toString());
        assertEquals("hello".hashCode(), view.hashCode());
    }

}
