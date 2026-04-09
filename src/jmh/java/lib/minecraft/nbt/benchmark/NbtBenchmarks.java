package dev.sbs.minecraftapi.nbt.benchmark;

import dev.sbs.minecraftapi.MinecraftApi;
import dev.sbs.minecraftapi.nbt.NbtFactory;
import dev.sbs.minecraftapi.nbt.io.buffer.NbtInputBuffer;
import dev.sbs.minecraftapi.nbt.io.buffer.NbtOutputBuffer;
import dev.sbs.minecraftapi.nbt.tags.TagType;
import dev.sbs.minecraftapi.nbt.tags.array.ByteArrayTag;
import dev.sbs.minecraftapi.nbt.tags.array.IntArrayTag;
import dev.sbs.minecraftapi.nbt.tags.array.LongArrayTag;
import dev.sbs.minecraftapi.nbt.tags.collection.CompoundTag;
import dev.sbs.minecraftapi.nbt.tags.collection.ListTag;
import dev.sbs.minecraftapi.nbt.tags.primitive.IntTag;
import dev.sbs.minecraftapi.nbt.tags.primitive.StringTag;
import dev.simplified.stream.Compression;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for the NBT library.
 *
 * <p>Two corpora are exercised:</p>
 * <ul>
 *   <li><b>Synthetic</b> - a hand-built compound covering every tag type with realistic sizes.
 *       Always available.</li>
 *   <li><b>Auction</b> - real Hypixel auction-house item NBTs loaded from a local fixture file at
 *       {@code src/test/resources/nbt-bench-fixture/auctions.bin} (produced by
 *       {@code ./gradlew :minecraft-api:generateAuctionFixture}). Auction benchmarks are no-ops if
 *       the fixture is missing.</li>
 * </ul>
 *
 * <p>Run with:</p>
 * <pre>
 *   ./gradlew :minecraft-api:jmh -PjmhInclude=NbtBenchmarks
 * </pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class NbtBenchmarks {

    private static final Path AUCTION_FIXTURE = Paths.get("minecraft-api/src/test/resources/nbt-bench-fixture/auctions.bin");
    private static final Path AUCTION_FIXTURE_FALLBACK = Paths.get("src/test/resources/nbt-bench-fixture/auctions.bin");

    private NbtFactory nbt;

    // Synthetic corpus
    private CompoundTag syntheticCompound;
    private byte[] syntheticBytesUncompressed;
    private byte[] syntheticBytesGzipped;
    private String syntheticSnbt;

    // Auction corpus (loaded from fixture if present)
    private byte[][] auctionPayloads;
    private CompoundTag[] auctionCompounds;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        this.nbt = MinecraftApi.getNbtFactory();

        // Synthetic
        this.syntheticCompound = buildSyntheticCompound();
        this.syntheticBytesUncompressed = this.nbt.toByteArray(this.syntheticCompound);
        this.syntheticBytesGzipped = this.nbt.toByteArray(this.syntheticCompound, Compression.GZIP);
        this.syntheticSnbt = this.nbt.toSnbt(this.syntheticCompound);

        // Auction (optional)
        Path fixture = Files.exists(AUCTION_FIXTURE) ? AUCTION_FIXTURE
            : Files.exists(AUCTION_FIXTURE_FALLBACK) ? AUCTION_FIXTURE_FALLBACK : null;

        if (fixture == null) {
            this.auctionPayloads = new byte[0][];
            this.auctionCompounds = new CompoundTag[0];
            System.out.println("Auction fixture not found - auction benchmarks will be no-ops. " +
                "Run ./gradlew :minecraft-api:generateAuctionFixture with HYPIXEL_API_KEY set to populate it.");
            return;
        }

        try (DataInputStream in = new DataInputStream(Files.newInputStream(fixture))) {
            int count = in.readInt();
            this.auctionPayloads = new byte[count][];
            this.auctionCompounds = new CompoundTag[count];

            for (int i = 0; i < count; i++) {
                int len = in.readInt();
                byte[] payload = new byte[len];
                in.readFully(payload);
                this.auctionPayloads[i] = payload;
                this.auctionCompounds[i] = this.nbt.fromByteArray(payload);
            }
        }

        System.out.println("Loaded " + this.auctionPayloads.length + " auction NBT payloads from " + fixture);
    }

    /**
     * Builds a synthetic compound roughly the shape of a SkyBlock item NBT: nested compound, ExtraAttributes,
     * a list of compound enchants, lore-style string list, primitive arrays.
     */
    private static CompoundTag buildSyntheticCompound() {
        CompoundTag root = new CompoundTag(8);
        ListTag<CompoundTag> items = new ListTag<>(1);

        CompoundTag item = new CompoundTag(16);
        item.put("Count", new IntTag(1));
        item.put("id", new StringTag("minecraft:diamond_sword"));
        item.put("Damage", new IntTag(0));

        CompoundTag tag = new CompoundTag(16);
        tag.put("Unbreakable", new IntTag(1));
        tag.put("HideFlags", new IntTag(254));

        ListTag<CompoundTag> enchants = new ListTag<>(8);
        for (int i = 0; i < 8; i++) {
            CompoundTag e = new CompoundTag(2);
            e.put("id", new IntTag(i));
            e.put("lvl", new IntTag(5));
            enchants.add(e);
        }
        tag.put("ench", enchants);

        CompoundTag display = new CompoundTag(2);
        display.put("Name", new StringTag("\u00a76\u00a7lLegendary Aspect of the End"));
        ListTag<StringTag> lore = new ListTag<>(8);
        for (int i = 0; i < 8; i++) lore.add(new StringTag("\u00a77Line " + i + " of lore."));
        display.put("Lore", lore);
        tag.put("display", display);

        CompoundTag attrs = new CompoundTag(16);
        attrs.put("id", new StringTag("ASPECT_OF_THE_END"));
        attrs.put("uuid", new StringTag("00000000-0000-0000-0000-000000000000"));
        attrs.put("timestamp", new StringTag("1/1/2024 12:00 AM"));
        attrs.put("modifier", new StringTag("warped"));
        attrs.put("hot_potato_count", new IntTag(15));
        attrs.put("rarity_upgrades", new IntTag(1));
        attrs.put("dungeon_item_level", new IntTag(5));
        attrs.put("upgrade_level", new IntTag(7));
        // Some primitive arrays sized like skinblock data.
        attrs.put("byte_data", new ByteArrayTag(new byte[256]));
        attrs.put("int_data", new IntArrayTag(new int[64]));
        attrs.put("long_uuid", new LongArrayTag(0x1234567890abcdefL, 0x0fedcba987654321L));
        tag.put("ExtraAttributes", attrs);

        item.put("tag", tag);
        items.add(item);
        root.put("i", items);

        return root;
    }

    // ---------------------------------------------------------------------
    // Synthetic - binary deserialization
    // ---------------------------------------------------------------------

    @Benchmark
    public CompoundTag synthetic_deserialize_binary() {
        return this.nbt.fromByteArray(this.syntheticBytesUncompressed);
    }

    @Benchmark
    public CompoundTag synthetic_deserialize_binary_gzip() {
        return this.nbt.fromByteArray(this.syntheticBytesGzipped);
    }

    @Benchmark
    public CompoundTag synthetic_deserialize_snbt() {
        return this.nbt.fromSnbt(this.syntheticSnbt);
    }

    // ---------------------------------------------------------------------
    // Synthetic - binary serialization
    // ---------------------------------------------------------------------

    @Benchmark
    public byte[] synthetic_serialize_binary() {
        return this.nbt.toByteArray(this.syntheticCompound);
    }

    @Benchmark
    public byte[] synthetic_serialize_binary_gzip() {
        return this.nbt.toByteArray(this.syntheticCompound, Compression.GZIP);
    }

    @Benchmark
    public String synthetic_serialize_snbt() {
        return this.nbt.toSnbt(this.syntheticCompound);
    }

    @Benchmark
    public String synthetic_serialize_json() {
        return this.nbt.toJson(this.syntheticCompound);
    }

    // ---------------------------------------------------------------------
    // Synthetic - low-level buffer paths (no compression overhead)
    // ---------------------------------------------------------------------

    @Benchmark
    public CompoundTag synthetic_inputBuffer_direct() throws IOException {
        NbtInputBuffer buffer = new NbtInputBuffer(this.syntheticBytesUncompressed);
        buffer.readByte();
        buffer.readUTF();
        return buffer.readCompoundTag();
    }

    @Benchmark
    public byte[] synthetic_outputBuffer_direct() throws IOException {
        NbtOutputBuffer buffer = new NbtOutputBuffer();
        buffer.writeByte(TagType.COMPOUND.getId());
        buffer.writeUTF("");
        buffer.writeCompoundTag(this.syntheticCompound);
        return buffer.toByteArray();
    }

    // ---------------------------------------------------------------------
    // Synthetic - tag traversal cost (touches collection iteration paths)
    // ---------------------------------------------------------------------

    @Benchmark
    public int synthetic_traverse_compound(Blackhole bh) {
        int counter = 0;
        for (Map.Entry<String, dev.sbs.minecraftapi.nbt.tags.Tag<?>> entry : this.syntheticCompound) {
            bh.consume(entry.getKey());
            counter++;
        }
        return counter;
    }

    @Benchmark
    public long synthetic_traverse_intArray() {
        ListTag<CompoundTag> items = this.syntheticCompound.getListTag("i");
        CompoundTag firstItem = items.get(0);
        CompoundTag tag = firstItem.getTag("tag");
        CompoundTag attrs = tag.getTag("ExtraAttributes");
        IntArrayTag intArr = attrs.getTag("int_data");
        long sum = 0;
        int[] values = intArr.getValue();
        for (int v : values) sum += v;
        return sum;
    }

    // ---------------------------------------------------------------------
    // Auction corpus - real-world payloads
    // ---------------------------------------------------------------------

    @Benchmark
    public List<CompoundTag> auction_deserializeAll() {
        List<CompoundTag> out = new ArrayList<>(this.auctionPayloads.length);
        for (byte[] payload : this.auctionPayloads)
            out.add(this.nbt.fromByteArray(payload));
        return out;
    }

    @Benchmark
    public List<byte[]> auction_serializeAll() {
        List<byte[]> out = new ArrayList<>(this.auctionCompounds.length);
        for (CompoundTag c : this.auctionCompounds)
            out.add(this.nbt.toByteArray(c));
        return out;
    }

    @Benchmark
    public List<byte[]> auction_serializeAll_gzip() {
        List<byte[]> out = new ArrayList<>(this.auctionCompounds.length);
        for (CompoundTag c : this.auctionCompounds)
            out.add(this.nbt.toByteArray(c, Compression.GZIP));
        return out;
    }

    @Benchmark
    public List<String> auction_serializeAll_snbt() {
        List<String> out = new ArrayList<>(this.auctionCompounds.length);
        for (CompoundTag c : this.auctionCompounds)
            out.add(this.nbt.toSnbt(c));
        return out;
    }

    /**
     * The full deserialize-then-reserialize round trip - measures the realistic per-item cost in a
     * high-frequency auction-house poll scenario.
     */
    @Benchmark
    public int auction_roundTrip() {
        int sum = 0;
        for (byte[] payload : this.auctionPayloads) {
            CompoundTag c = this.nbt.fromByteArray(payload);
            byte[] reserialized = this.nbt.toByteArray(c);
            sum += reserialized.length;
        }
        return sum;
    }

}
