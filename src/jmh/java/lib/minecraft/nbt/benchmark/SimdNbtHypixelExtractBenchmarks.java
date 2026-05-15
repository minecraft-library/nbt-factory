package lib.minecraft.nbt.benchmark;

import lib.minecraft.nbt.NbtFactory;
import lib.minecraft.nbt.borrow.BorrowedByteTag;
import lib.minecraft.nbt.borrow.BorrowedCompoundTag;
import lib.minecraft.nbt.borrow.BorrowedIntTag;
import lib.minecraft.nbt.borrow.BorrowedListTag;
import lib.minecraft.nbt.borrow.BorrowedShortTag;
import lib.minecraft.nbt.borrow.BorrowedStringTag;
import lib.minecraft.nbt.borrow.BorrowedTag;
import lib.minecraft.nbt.tags.Tag;
import lib.minecraft.nbt.tags.collection.CompoundTag;
import lib.minecraft.nbt.tags.collection.ListTag;
import lib.minecraft.nbt.tags.primitive.ByteTag;
import lib.minecraft.nbt.tags.primitive.IntTag;
import lib.minecraft.nbt.tags.primitive.ShortTag;
import lib.minecraft.nbt.tags.primitive.StringTag;
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Parse + extract benchmark mirroring {@code simdnbt/benches/compare_hypixel.rs}.
 *
 * <p>Loads the {@code hypixel.nbt} corpus payload (a captured SkyBlock auction-house response,
 * ~18 KB uncompressed, ~50 items) and, on each invocation, parses the NBT and walks every item
 * extracting the same fields simdnbt's reference benchmark extracts: id, Damage, Count,
 * SkullOwner head texture, ExtraAttributes id / modifier / timestamp, display Name / Lore /
 * color, has-glint flag, and the enchantments map.</p>
 *
 * <p>The shape of the extracted records ({@link Item}, {@link ItemDisplay}) mirrors
 * {@code compare_hypixel.rs:74-99} byte-for-byte so the two benchmarks measure the same work.</p>
 *
 * <p>Run with:</p>
 * <pre>
 *   ./gradlew jmh -PjmhInclude=SimdNbtHypixelExtract
 * </pre>
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 5)
@Fork(1)
public class SimdNbtHypixelExtractBenchmarks {

    private static final Path HYPIXEL_FIXTURE =
        Paths.get("src/test/resources/simdnbt-corpus/hypixel.nbt");

    private byte[] payload;
    private int payloadBytes;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        this.payload = Files.readAllBytes(HYPIXEL_FIXTURE);
        this.payloadBytes = this.payload.length;
        System.err.println("payload-size: hypixel.nbt=" + this.payloadBytes);
    }

    @Benchmark
    public List<Item> parseAndExtract(BytesProcessed bytes) {
        bytes.payloadBytes += this.payloadBytes;
        CompoundTag root = NbtFactory.fromByteArray(this.payload);
        return extractItems(root);
    }

    /**
     * Borrow-mode counterpart to {@link #parseAndExtract(BytesProcessed)} - parses the same
     * payload via {@link NbtFactory#borrowFromByteArray(byte[])} and walks the tape selectively,
     * decoding only the fields required to populate the {@link Item} / {@link ItemDisplay}
     * records.
     *
     * <p>String fields ({@code id}, {@code Name}, {@code Lore}, etc.) call
     * {@link BorrowedStringTag#getValue()} only on the values actually extracted, preserving the
     * borrow win for fields that go unread. Compounds and lists that don't contain any extracted
     * field are never traversed.</p>
     */
    @Benchmark
    public List<Item> parseAndExtractBorrow(BytesProcessed bytes) {
        bytes.payloadBytes += this.payloadBytes;
        BorrowedCompoundTag root = NbtFactory.borrowFromByteArray(this.payload);
        return extractItemsBorrow(root);
    }

    /**
     * Walks the auction NBT root and pulls every item's fields into a flat list.
     *
     * <p>Mirrors {@code simdnbt_items_from_nbt} at {@code compare_hypixel.rs:101-170}. Items that
     * lack an {@code id} field round-trip as {@code null} in the output list, matching the Rust
     * benchmark's {@code Vec<Option<Item>>}.</p>
     */
    @SuppressWarnings("unchecked")
    private static List<Item> extractItems(CompoundTag root) {
        ListTag<CompoundTag> auctionItems = root.getListTag("i");
        if (auctionItems == null)
            return new ArrayList<>();

        List<Item> items = new ArrayList<>(auctionItems.size());
        for (CompoundTag itemNbt : auctionItems) {
            if (!itemNbt.containsKey("id")) {
                items.add(null);
                continue;
            }

            CompoundTag tag = itemNbt.getTag("tag");
            if (tag == null) {
                items.add(null);
                continue;
            }

            CompoundTag extraAttrs = tag.getTag("ExtraAttributes");
            CompoundTag display = tag.getTag("display");

            short id = shortValue(itemNbt, "id");
            short damage = shortValue(itemNbt, "Damage");
            byte count = byteValue(itemNbt, "Count");

            String headTextureId = extractHeadTextureId(tag);
            String skyblockId = stringValue(extraAttrs, "id");
            String reforge = stringValue(extraAttrs, "modifier");
            String timestamp = stringValue(extraAttrs, "timestamp");

            String displayName = stringValue(display, "Name");
            if (displayName == null) displayName = "";
            List<String> lore = extractLore(display);
            Integer color = intValueBoxed(display, "color");
            boolean hasGlint = extraAttrs != null && extraAttrs.containsKey("ench");

            Map<String, Integer> enchantments = extractEnchantments(extraAttrs);

            items.add(new Item(id, damage, count,
                headTextureId, skyblockId, reforge,
                new ItemDisplay(displayName, lore, hasGlint, color),
                enchantments, timestamp));
        }
        return items;
    }

    @SuppressWarnings("unchecked")
    private static List<String> extractLore(CompoundTag display) {
        if (display == null)
            return new ArrayList<>();
        ListTag<StringTag> lore = display.getListTag("Lore");
        if (lore == null)
            return new ArrayList<>();
        List<String> out = new ArrayList<>(lore.size());
        for (StringTag s : lore)
            out.add(s.getValue());
        return out;
    }

    private static Map<String, Integer> extractEnchantments(CompoundTag extraAttrs) {
        if (extraAttrs == null)
            return new HashMap<>();
        CompoundTag enchants = extraAttrs.getTag("enchantments");
        if (enchants == null)
            return new HashMap<>();
        Map<String, Integer> out = new HashMap<>(enchants.size());
        for (Map.Entry<String, Tag<?>> e : enchants) {
            int value = (e.getValue() instanceof IntTag intTag) ? intTag.getValue() : 0;
            out.put(e.getKey(), value);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static String extractHeadTextureId(CompoundTag tag) {
        CompoundTag skullOwner = tag.getTag("SkullOwner");
        if (skullOwner == null) return null;
        CompoundTag properties = skullOwner.getTag("Properties");
        if (properties == null) return null;
        ListTag<CompoundTag> textures = properties.getListTag("textures");
        if (textures == null || textures.isEmpty()) return null;
        CompoundTag first = textures.get(0);
        return stringValue(first, "Value");
    }

    private static short shortValue(CompoundTag c, String key) {
        Tag<?> t = c.get(key);
        return (t instanceof ShortTag s) ? s.getValue() : 0;
    }

    private static byte byteValue(CompoundTag c, String key) {
        Tag<?> t = c.get(key);
        return (t instanceof ByteTag b) ? b.getValue() : 0;
    }

    private static Integer intValueBoxed(CompoundTag c, String key) {
        if (c == null) return null;
        Tag<?> t = c.get(key);
        return (t instanceof IntTag i) ? i.getValue() : null;
    }

    private static String stringValue(CompoundTag c, String key) {
        if (c == null) return null;
        Tag<?> t = c.get(key);
        return (t instanceof StringTag s) ? s.getValue() : null;
    }

    // ----------------------------------------------------------------------------------
    // Borrow-mode mirror of the extraction helpers above.
    // ----------------------------------------------------------------------------------

    /**
     * Borrow-mode mirror of {@link #extractItems(CompoundTag)}. Walks the same auction NBT shape
     * via {@link BorrowedCompoundTag} accessors so only the fields actually extracted decode their
     * payloads.
     */
    private static List<Item> extractItemsBorrow(BorrowedCompoundTag root) {
        BorrowedTag<?> auctionItemsTag = root.get("i");
        if (!(auctionItemsTag instanceof BorrowedListTag auctionItems))
            return new ArrayList<>();

        List<Item> items = new ArrayList<>(auctionItems.size());
        Iterator<BorrowedTag<?>> entries = auctionItems.iterator();
        while (entries.hasNext()) {
            BorrowedTag<?> entry = entries.next();
            if (!(entry instanceof BorrowedCompoundTag itemNbt) || !itemNbt.containsKey("id")) {
                items.add(null);
                continue;
            }

            BorrowedTag<?> tagTag = itemNbt.get("tag");
            if (!(tagTag instanceof BorrowedCompoundTag tag)) {
                items.add(null);
                continue;
            }

            BorrowedCompoundTag extraAttrs = compoundOrNull(tag.get("ExtraAttributes"));
            BorrowedCompoundTag display = compoundOrNull(tag.get("display"));

            short id = shortValueBorrow(itemNbt, "id");
            short damage = shortValueBorrow(itemNbt, "Damage");
            byte count = byteValueBorrow(itemNbt, "Count");

            String headTextureId = extractHeadTextureIdBorrow(tag);
            String skyblockId = stringValueBorrow(extraAttrs, "id");
            String reforge = stringValueBorrow(extraAttrs, "modifier");
            String timestamp = stringValueBorrow(extraAttrs, "timestamp");

            String displayName = stringValueBorrow(display, "Name");
            if (displayName == null) displayName = "";
            List<String> lore = extractLoreBorrow(display);
            Integer color = intValueBoxedBorrow(display, "color");
            boolean hasGlint = extraAttrs != null && extraAttrs.containsKey("ench");

            Map<String, Integer> enchantments = extractEnchantmentsBorrow(extraAttrs);

            items.add(new Item(id, damage, count,
                headTextureId, skyblockId, reforge,
                new ItemDisplay(displayName, lore, hasGlint, color),
                enchantments, timestamp));
        }
        return items;
    }

    private static List<String> extractLoreBorrow(BorrowedCompoundTag display) {
        if (display == null)
            return new ArrayList<>();
        BorrowedTag<?> loreTag = display.get("Lore");
        if (!(loreTag instanceof BorrowedListTag lore))
            return new ArrayList<>();
        List<String> out = new ArrayList<>(lore.size());
        Iterator<BorrowedTag<?>> it = lore.iterator();
        while (it.hasNext()) {
            BorrowedTag<?> s = it.next();
            // Each Lore entry is a string; only here do we pay the modified-UTF-8 decode.
            if (s instanceof BorrowedStringTag bs)
                out.add(bs.getValue());
        }
        return out;
    }

    private static Map<String, Integer> extractEnchantmentsBorrow(BorrowedCompoundTag extraAttrs) {
        if (extraAttrs == null)
            return new HashMap<>();
        BorrowedTag<?> enchantsTag = extraAttrs.get("enchantments");
        if (!(enchantsTag instanceof BorrowedCompoundTag enchants))
            return new HashMap<>();
        Map<String, Integer> out = new HashMap<>(enchants.size());
        for (Map.Entry<String, BorrowedTag<?>> e : enchants.entries()) {
            int value = (e.getValue() instanceof BorrowedIntTag bi) ? bi.getIntValue() : 0;
            out.put(e.getKey(), value);
        }
        return out;
    }

    private static String extractHeadTextureIdBorrow(BorrowedCompoundTag tag) {
        BorrowedCompoundTag skullOwner = compoundOrNull(tag.get("SkullOwner"));
        if (skullOwner == null) return null;
        BorrowedCompoundTag properties = compoundOrNull(skullOwner.get("Properties"));
        if (properties == null) return null;
        BorrowedTag<?> texturesTag = properties.get("textures");
        if (!(texturesTag instanceof BorrowedListTag textures) || textures.isEmpty()) return null;
        BorrowedTag<?> first = textures.get(0);
        if (!(first instanceof BorrowedCompoundTag firstCompound)) return null;
        return stringValueBorrow(firstCompound, "Value");
    }

    private static BorrowedCompoundTag compoundOrNull(BorrowedTag<?> tag) {
        return (tag instanceof BorrowedCompoundTag c) ? c : null;
    }

    private static short shortValueBorrow(BorrowedCompoundTag c, String key) {
        if (c == null) return 0;
        BorrowedTag<?> t = c.get(key);
        return (t instanceof BorrowedShortTag s) ? s.getShortValue() : 0;
    }

    private static byte byteValueBorrow(BorrowedCompoundTag c, String key) {
        if (c == null) return 0;
        BorrowedTag<?> t = c.get(key);
        return (t instanceof BorrowedByteTag b) ? b.getByteValue() : 0;
    }

    private static Integer intValueBoxedBorrow(BorrowedCompoundTag c, String key) {
        if (c == null) return null;
        BorrowedTag<?> t = c.get(key);
        return (t instanceof BorrowedIntTag i) ? i.getIntValue() : null;
    }

    private static String stringValueBorrow(BorrowedCompoundTag c, String key) {
        if (c == null) return null;
        BorrowedTag<?> t = c.get(key);
        return (t instanceof BorrowedStringTag s) ? s.getValue() : null;
    }

    /**
     * Hypixel auction-house item record, mirroring {@code compare_hypixel.rs:74-89}.
     */
    public record Item(short id, short damage, byte count,
                       String headTextureId, String skyblockId, String reforge,
                       ItemDisplay display,
                       Map<String, Integer> enchantments, String timestamp) {}

    /**
     * Cosmetic display fields, mirroring {@code compare_hypixel.rs:91-99}.
     */
    public record ItemDisplay(String name, List<String> lore,
                              boolean hasGlint, Integer color) {}

    /**
     * Per-thread aux counter exposing the bytes processed by each invocation. JMH adds
     * {@code "secondaryMetrics": {"payloadBytes": {...}}} to the JSON output so the report
     * script can compute MiB/s without relying on a hard-coded fallback table.
     */
    @AuxCounters(AuxCounters.Type.OPERATIONS)
    @State(Scope.Thread)
    public static class BytesProcessed {

        public long payloadBytes;

        @Setup(Level.Iteration)
        public void clear() {
            this.payloadBytes = 0;
        }

    }

}
