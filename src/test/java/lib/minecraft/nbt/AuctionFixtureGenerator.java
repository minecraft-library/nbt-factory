package lib.minecraft.nbt;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lib.minecraft.nbt.tags.collection.CompoundTag;
import org.jetbrains.annotations.NotNull;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.IntStream;

/**
 * Fetches the full SkyBlock auction house from the public Hypixel endpoint and writes every item NBT
 * payload to a local fixture file consumed by the JMH benchmarks under {@code src/jmh}.
 *
 * <p>Run via Gradle: {@code ./gradlew generateAuctionFixture}. No API key required - the endpoint
 * {@code https://api.hypixel.net/v2/skyblock/auctions} is public. Idempotent - skips the fetch if the
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

    private static final String ENDPOINT = "https://api.hypixel.net/v2/skyblock/auctions?page=";

    /**
     * Default output path for the JMH benchmark fixture, relative to the project directory.
     * Override by passing a different path as the first CLI argument.
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

        HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

        long start = System.currentTimeMillis();
        JsonObject firstPage = fetchPage(http, 0);
        int totalPages = firstPage.get("totalPages").getAsInt();
        System.out.println("Discovered " + totalPages + " auction pages; fetching in parallel.");

        ConcurrentLinkedQueue<byte[]> payloads = new ConcurrentLinkedQueue<>();
        extractPayloads(firstPage, payloads);

        IntStream.range(1, totalPages)
            .parallel()
            .forEach(page -> extractPayloads(fetchPage(http, page), payloads));

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

        List<byte[]> snapshot = List.copyOf(payloads);
        long sanityStart = System.currentTimeMillis();
        int parsed = 0;
        for (byte[] payload : snapshot) {
            CompoundTag root = NbtFactory.fromByteArray(payload);
            if (!root.isEmpty()) parsed++;
        }
        long sanityEnd = System.currentTimeMillis();
        System.out.println("Sanity-parsed " + parsed + " payloads in " + (sanityEnd - sanityStart) + "ms.");
    }

    private static @NotNull JsonObject fetchPage(@NotNull HttpClient http, int page) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(ENDPOINT + page))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();

        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
                throw new IOException("Page " + page + " returned HTTP " + response.statusCode());

            return JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to fetch auction page " + page, ex);
        }
    }

    private static void extractPayloads(@NotNull JsonObject page, @NotNull ConcurrentLinkedQueue<byte[]> sink) {
        JsonArray auctions = page.getAsJsonArray("auctions");
        if (auctions == null) return;

        Base64.Decoder decoder = Base64.getDecoder();
        for (JsonElement entry : auctions) {
            JsonElement itemBytes = entry.getAsJsonObject().get("item_bytes");
            if (itemBytes == null || itemBytes.isJsonNull()) continue;

            String encoded = itemBytes.isJsonObject()
                ? itemBytes.getAsJsonObject().get("data").getAsString()
                : itemBytes.getAsString();

            byte[] decoded = decoder.decode(encoded);
            if (decoded.length > 0) sink.add(decoded);
        }
    }

}
