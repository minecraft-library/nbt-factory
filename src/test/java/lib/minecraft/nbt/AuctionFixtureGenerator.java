package dev.sbs.minecraftapi.nbt;

import dev.sbs.minecraftapi.MinecraftApi;
import dev.sbs.minecraftapi.client.hypixel.request.HypixelContract;
import dev.sbs.minecraftapi.client.hypixel.response.skyblock.SkyBlockAuction;
import dev.sbs.minecraftapi.client.hypixel.response.skyblock.SkyBlockAuctions;
import dev.sbs.minecraftapi.nbt.tags.collection.CompoundTag;
import dev.sbs.minecraftapi.skyblock.common.NbtContent;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.util.SystemUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Fetches the full SkyBlock auction house from Hypixel and writes every item NBT payload to a local
 * fixture file consumed by the JMH benchmarks under {@code src/jmh}.
 *
 * <p>Run via Gradle: {@code ./gradlew :minecraft-api:generateAuctionFixture}.
 * Requires the {@code HYPIXEL_API_KEY} environment variable. Idempotent - skips the fetch if the
 * fixture already exists.</p>
 *
 * <p>Fixture format ({@link DataOutputStream}-encoded big-endian):</p>
 * <pre>
 *   int  count
 *   for each entry:
 *     int  length
 *     byte[length]   gzip-compressed NBT bytes (the raw item-nbt payload Hypixel returns)
 * </pre>
 */
public final class AuctionFixtureGenerator {

    /**
     * Default output path for the JMH benchmark fixture, relative to the {@code minecraft-api} module
     * directory. Override by passing a different path as the first CLI argument.
     */
    public static final Path AUCTION_FIXTURE = Paths.get("src/test/resources/nbt-bench-fixture/auctions.bin");

    private AuctionFixtureGenerator() { }

    public static void main(String @NotNull [] args) throws IOException {
        Path output = args.length > 0 ? Paths.get(args[0]) : AUCTION_FIXTURE;

        if (Files.exists(output)) {
            long size = Files.size(output);
            System.out.println("Auction fixture already exists at " + output.toAbsolutePath() + " (" + size + " bytes), skipping fetch.");
            return;
        }

        MinecraftApi.getKeyManager().add(SystemUtil.getEnvPair("HYPIXEL_API_KEY"));

        HypixelContract hypixelEndpoints = MinecraftApi.getClient(HypixelContract.class).getContract();
        SkyBlockAuctions firstPage = hypixelEndpoints.getAuctions();
        int totalPages = firstPage.getTotalPages();

        long start = System.currentTimeMillis();
        ConcurrentList<byte[]> payloads = IntStream.range(0, totalPages)
            .parallel()
            .mapToObj(page -> page == 0 ? firstPage : hypixelEndpoints.getAuctions(page))
            .flatMap(response -> response.getAuctions().parallelStream())
            .map(SkyBlockAuction::getItem)
            .map(NbtContent::getData)
            .filter(Objects::nonNull)
            .collect(Concurrent.toList());
        long end = System.currentTimeMillis();
        System.out.println("Fetched + extracted " + payloads.size() + " NBT payloads in " + (end - start) + "ms.");

        Files.createDirectories(output.getParent());
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(output))) {
            out.writeInt(payloads.size());

            for (byte[] payload : payloads) {
                out.writeInt(payload.length);
                out.write(payload);
            }
        }

        System.out.println("Saved fixture to " + output.toAbsolutePath());

        // Sanity check: every payload should round-trip cleanly through NbtFactory.
        long sanityStart = System.currentTimeMillis();
        int parsed = 0;
        for (byte[] payload : payloads) {
            CompoundTag root = MinecraftApi.getNbtFactory().fromByteArray(payload);
            if (!root.isEmpty()) parsed++;
        }
        long sanityEnd = System.currentTimeMillis();
        System.out.println("Sanity-parsed " + parsed + " payloads in " + (sanityEnd - sanityStart) + "ms.");
    }

}
