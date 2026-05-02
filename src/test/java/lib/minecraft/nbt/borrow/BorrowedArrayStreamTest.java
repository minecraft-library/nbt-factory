package lib.minecraft.nbt.borrow;

import lib.minecraft.nbt.tags.array.ByteArrayTag;
import lib.minecraft.nbt.tags.array.IntArrayTag;
import lib.minecraft.nbt.tags.array.LongArrayTag;
import lib.minecraft.nbt.tags.collection.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the Phase D4 stream and {@code forEach} accessors on the borrowed array-tag types.
 *
 * <p>Two axes are covered for the long variant:</p>
 *
 * <ul>
 *   <li><b>Sum parity</b> - {@code longStream().sum()} matches {@code Arrays.stream(toLongArray()).sum()}
 *       for sizes {@code 0, 1, 10, 1023, 65537} (covers the 64 KiB chunk boundary).</li>
 *   <li><b>Parallel determinism</b> - {@code longStream().parallel().sum()} matches the serial sum
 *       (validates {@link java.util.Spliterator#trySplit()} correctness, the highest-risk subitem
 *       per the ResearchPack risk register).</li>
 * </ul>
 *
 * <p>The byte variant is exercised via {@code forEachByte} - sequence parity against
 * {@code toByteArray()}. The int variant is exercised via {@code forEachInt} - the per-element
 * spliterator path was retired in Phase E1 because the bulk-byteswap materializing path beat it.</p>
 */
class BorrowedArrayStreamTest {

    @ParameterizedTest(name = "[{index}] size={0}")
    @ValueSource(ints = {0, 1, 10, 1023, 65537})
    @DisplayName("BorrowedLongArrayTag.longStream().sum() matches Arrays.stream(toLongArray()).sum()")
    void longStreamSumMatchesMaterializedSum(int size) {
        long[] payload = makeLongs(size);

        BorrowedLongArrayTag borrowed = borrowLongArray(payload);

        long viaToArray = Arrays.stream(borrowed.toLongArray()).sum();
        long viaStream = borrowed.longStream().sum();

        assertEquals(viaToArray, viaStream,
            "longStream sum mismatch at size " + size);
    }

    @ParameterizedTest(name = "[{index}] size={0}")
    @ValueSource(ints = {0, 1, 10, 1023, 65537})
    @DisplayName("BorrowedLongArrayTag.longStream().parallel().sum() matches serial sum")
    void longStreamParallelSumMatchesSerial(int size) {
        long[] payload = makeLongs(size);

        BorrowedLongArrayTag borrowed = borrowLongArray(payload);

        long serial = borrowed.longStream().sum();
        long parallel = borrowed.longStream().parallel().sum();

        assertEquals(serial, parallel,
            "Parallel longStream sum diverged from serial at size " + size);
    }

    @ParameterizedTest(name = "[{index}] size={0}")
    @ValueSource(ints = {0, 1, 10, 1023})
    @DisplayName("BorrowedIntArrayTag.forEachInt accumulates the same sequence as toIntArray()")
    void forEachIntSequenceMatches(int size) {
        int[] payload = makeInts(size);

        BorrowedIntArrayTag borrowed = borrowIntArray(payload);

        List<Integer> collected = new ArrayList<>(size);
        borrowed.forEachInt(collected::add);

        int[] expected = borrowed.toIntArray();
        assertEquals(expected.length, collected.size());
        for (int i = 0; i < expected.length; i++)
            assertEquals(expected[i], collected.get(i),
                "forEachInt sequence mismatch at index " + i);
    }

    @ParameterizedTest(name = "[{index}] size={0}")
    @ValueSource(ints = {0, 1, 10, 1023})
    @DisplayName("BorrowedLongArrayTag.forEachLong accumulates the same sequence as toLongArray()")
    void forEachLongSequenceMatches(int size) {
        long[] payload = makeLongs(size);

        BorrowedLongArrayTag borrowed = borrowLongArray(payload);

        List<Long> collected = new ArrayList<>(size);
        borrowed.forEachLong(collected::add);

        long[] expected = borrowed.toLongArray();
        assertEquals(expected.length, collected.size());
        for (int i = 0; i < expected.length; i++)
            assertEquals(expected[i], collected.get(i),
                "forEachLong sequence mismatch at index " + i);
    }

    @ParameterizedTest(name = "[{index}] size={0}")
    @ValueSource(ints = {0, 1, 10, 1023})
    @DisplayName("BorrowedByteArrayTag.forEachByte reads each byte exactly once in order")
    void forEachByteReadsEachByteInOrder(int size) {
        byte[] payload = makeBytes(size);

        BorrowedByteArrayTag borrowed = borrowByteArray(payload);

        byte[] collected = new byte[size];
        AtomicInteger index = new AtomicInteger();
        borrowed.forEachByte(b -> collected[index.getAndIncrement()] = b);

        assertEquals(size, index.get(), "forEachByte invocation count");
        assertArrayEquals(borrowed.toByteArray(), collected);
    }

    @Test
    @DisplayName("Empty LongArray.longStream() is empty - sum is 0, no terminal-op error")
    void emptyLongStreamIsEmpty() {
        BorrowedLongArrayTag borrowed = borrowLongArray(new long[0]);
        LongStream stream = borrowed.longStream();
        assertEquals(0, stream.count());
        assertEquals(0L, borrowed.longStream().sum());
    }

    @Test
    @DisplayName("Empty ByteArray.forEachByte is a no-op")
    void emptyForEachByteIsNoOp() {
        BorrowedByteArrayTag borrowed = borrowByteArray(new byte[0]);
        AtomicInteger calls = new AtomicInteger();
        borrowed.forEachByte(b -> calls.incrementAndGet());
        assertEquals(0, calls.get());
    }

    @Test
    @DisplayName("forEachInt vs Arrays.stream(toIntArray()).sum produce identical aggregate results")
    void forEachIntMatchesToIntArraySum() {
        int[] payload = makeInts(1023);
        BorrowedIntArrayTag borrowed = borrowIntArray(payload);

        AtomicLong tally = new AtomicLong();
        borrowed.forEachInt(v -> tally.addAndGet(v));

        long viaArray = Arrays.stream(borrowed.toIntArray()).asLongStream().sum();
        assertEquals(viaArray, tally.get());
    }

    @Test
    @DisplayName("forEachLong vs LongStream.sum produce identical aggregate results")
    void forEachLongMatchesLongStreamSum() {
        long[] payload = makeLongs(1023);
        BorrowedLongArrayTag borrowed = borrowLongArray(payload);

        AtomicLong tally = new AtomicLong();
        borrowed.forEachLong(v -> tally.addAndGet(v));

        long viaStream = borrowed.longStream().sum();
        assertEquals(viaStream, tally.get());
    }

    // ------------------------------------------------------------------
    // Fixtures.
    // ------------------------------------------------------------------

    private static int[] makeInts(int size) {
        int[] arr = new int[size];
        // Spread values across the negative + positive range; include the boundary values at the
        // ends so byteswap correctness is checked at every fixture size.
        for (int i = 0; i < size; i++)
            arr[i] = (i * 1_000_003) - (size >> 1);
        if (size > 0) {
            arr[0] = Integer.MIN_VALUE;
            arr[size - 1] = Integer.MAX_VALUE;
        }
        return arr;
    }

    private static long[] makeLongs(int size) {
        long[] arr = new long[size];
        for (int i = 0; i < size; i++)
            arr[i] = ((long) i * 1_000_000_007L) - ((long) size << 16);
        if (size > 0) {
            arr[0] = Long.MIN_VALUE;
            arr[size - 1] = Long.MAX_VALUE;
        }
        return arr;
    }

    private static byte[] makeBytes(int size) {
        byte[] arr = new byte[size];
        for (int i = 0; i < size; i++)
            arr[i] = (byte) (i ^ 0xA5);
        return arr;
    }

    private static BorrowedIntArrayTag borrowIntArray(int[] payload) {
        CompoundTag root = new CompoundTag();
        root.put("ints", new IntArrayTag(payload));
        BorrowedCompoundTag borrowedRoot = Tape.encode(root).root();
        BorrowedIntArrayTag tag = (BorrowedIntArrayTag) borrowedRoot.get("ints");
        assertNotNull(tag);
        assertEquals(payload.length, tag.size());
        return tag;
    }

    private static BorrowedLongArrayTag borrowLongArray(long[] payload) {
        CompoundTag root = new CompoundTag();
        root.put("longs", new LongArrayTag(payload));
        BorrowedCompoundTag borrowedRoot = Tape.encode(root).root();
        BorrowedLongArrayTag tag = (BorrowedLongArrayTag) borrowedRoot.get("longs");
        assertNotNull(tag);
        assertEquals(payload.length, tag.size());
        return tag;
    }

    private static BorrowedByteArrayTag borrowByteArray(byte[] payload) {
        CompoundTag root = new CompoundTag();
        root.put("bytes", new ByteArrayTag(payload));
        BorrowedCompoundTag borrowedRoot = Tape.encode(root).root();
        BorrowedByteArrayTag tag = (BorrowedByteArrayTag) borrowedRoot.get("bytes");
        assertNotNull(tag);
        assertEquals(payload.length, tag.size());
        return tag;
    }

    // Suppress unused; we intentionally check the assertTrue on a clamp to keep test discovery
    // unambiguous when extending this class.
    @SuppressWarnings("unused")
    private static void assertNonNegative(int v) {
        assertTrue(v >= 0);
    }

}
