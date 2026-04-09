package dev.sbs.minecraftapi.nbt.io;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.Nullable;

/**
 * Zero-allocation lookup table of well-known Minecraft NBT key strings.
 *
 * <p>Every compound-key read during deserialization normally allocates a fresh {@link String}
 * instance, even when the same key appears hundreds of times across a batch of payloads. This
 * class replaces that with a static table of canonical strings matched against the raw UTF-8
 * bytes in the input buffer without ever constructing a temporary {@link String} for the lookup.
 * On a hit the caller returns the shared interned candidate and pays zero allocation.</p>
 *
 * <p>The vocabulary is drawn from the official Minecraft wire-format specifications on
 * <a href="https://minecraft.wiki/w/NBT_format">minecraft.wiki</a>:</p>
 * <ul>
 *   <li><a href="https://minecraft.wiki/w/Player.dat_format">Player.dat format</a> - player and ItemStack keys</li>
 *   <li><a href="https://minecraft.wiki/w/Entity_format">Entity format</a> - entity/mob keys</li>
 *   <li><a href="https://minecraft.wiki/w/Block_entity_format">Block entity format</a> - tile entity keys</li>
 *   <li><a href="https://minecraft.wiki/w/Chunk_format">Chunk format</a> - level/chunk keys</li>
 * </ul>
 *
 * <p>Keys are grouped by source type ({@link #ITEM_KEYS}, {@link #ENTITY_KEYS},
 * {@link #BLOCK_ENTITY_KEYS}, {@link #PLAYER_KEYS}, {@link #CHUNK_KEYS}, {@link #SKYBLOCK_KEYS})
 * for documentation; the lookup table merges them into a single length-bucketed structure for
 * {@code O(1)} length narrowing followed by a short linear byte-compare scan. Each key in the
 * input lists must be ASCII - the match path relies on byte-level compares, not code-point
 * comparison, and ASCII bytes have identical encoding under modified UTF-8 and standard UTF-8 so
 * the match is valid regardless of which encoding the payload actually uses.</p>
 *
 * <p>Extending the vocabulary is a one-line addition to whichever type-specific list fits best.
 * Duplicates across lists are harmless (the static initializer dedupes), so categorization can
 * reflect semantic intent rather than forcing a single canonical home for every key.</p>
 */
@UtilityClass
public final class NbtKnownKeys {

    // ------------------------------------------------------------------
    // ItemStack + item tag subcompound vocabulary
    // ------------------------------------------------------------------

    /**
     * Keys for the ItemStack root, {@code tag} subcompound, {@code display} subcompound,
     * enchantment entries, and attribute-modifier entries.
     */
    public static final String[] ITEM_KEYS = {
        "id", "Count", "Slot", "tag", "Damage",
        "Unbreakable", "HideFlags", "RepairCost", "CustomModelData",
        "Enchantments", "StoredEnchantments", "AttributeModifiers",
        "CanDestroy", "CanPlaceOn", "BlockEntityTag", "BlockStateTag",
        "Trim", "display", "Name", "Lore", "color", "lvl",
        "AttributeName", "Operation", "Amount", "UUID",
        "ench" // pre-1.13 legacy enchantment list name
    };

    // ------------------------------------------------------------------
    // Entity (base + mob) vocabulary
    // ------------------------------------------------------------------

    /**
     * Keys for entity root tags and common mob subtags.
     */
    public static final String[] ENTITY_KEYS = {
        "Pos", "Motion", "Rotation",
        "Air", "Fire", "Health", "FallDistance", "FallFlying",
        "OnGround", "Invulnerable", "PortalCooldown",
        "HurtTime", "HurtByTimestamp", "DeathTime", "AbsorptionAmount",
        "CustomName", "CustomNameVisible", "Tags", "Passengers",
        "NoGravity", "Silent", "Glowing", "HasVisualFire", "TicksFrozen",
        "Team", "Brain", "LeftHanded", "Pose",
        "NoAI", "PersistenceRequired", "CanPickUpLoot",
        "HandItems", "ArmorItems", "HandDropChances", "ArmorDropChances",
        "Attributes", "ActiveEffects", "Effects",
        "Age", "Bred", "Owner", "Tame", "Variant", "Saddle", "Leash"
    };

    // ------------------------------------------------------------------
    // Block entity vocabulary
    // ------------------------------------------------------------------

    /**
     * Keys for block entities (tile entities) - generic plus the most common type-specific tags
     * (chest/container, furnace, brewing, sign, spawner, command block, beacon, beehive, skull).
     */
    public static final String[] BLOCK_ENTITY_KEYS = {
        "x", "y", "z", "keepPacked",
        "Items", "LootTable", "LootTableSeed",
        "BurnTime", "CookTime", "CookTimeTotal", "RecipesUsed",
        "BrewTime", "Fuel",
        "Text1", "Text2", "Text3", "Text4", "GlowingText",
        "front_text", "back_text", "is_waxed", "has_glowing_text",
        "SpawnData", "SpawnPotentials", "Delay",
        "MaxSpawnDelay", "MinSpawnDelay", "MaxNearbyEntities",
        "RequiredPlayerRange", "SpawnRange", "SpawnCount",
        "Command", "LastExecution", "LastOutput", "SuccessCount",
        "TrackOutput", "UpdateLastExecution", "auto", "conditionMet", "powered",
        "Levels", "Primary", "Secondary", "Lock",
        "bees", "flower_pos", "profile", "Patterns"
    };

    // ------------------------------------------------------------------
    // Player.dat vocabulary
    // ------------------------------------------------------------------

    /**
     * Keys specific to the player save format - abilities, inventory, xp, food, spawn, etc.
     */
    public static final String[] PLAYER_KEYS = {
        "abilities", "flySpeed", "walkSpeed", "flying", "mayfly",
        "instabuild", "mayBuild", "invulnerable",
        "Inventory", "EnderItems", "SelectedItemSlot", "SelectedItem",
        "ShoulderEntityLeft", "ShoulderEntityRight",
        "XpLevel", "XpTotal", "XpP", "XpSeed", "Score",
        "foodLevel", "foodSaturationLevel", "foodExhaustionLevel", "foodTickTimer",
        "SleepTimer", "spawnForced", "SpawnX", "SpawnY", "SpawnZ",
        "playerGameType", "previousPlayerGameType",
        "Dimension", "seenCredits", "recipeBook",
        "LastDeathLocation", "RootVehicle",
        "warden_spawn_tracker"
    };

    // ------------------------------------------------------------------
    // Chunk / Level format vocabulary
    // ------------------------------------------------------------------

    /**
     * Keys for the chunk NBT file format (region/anvil chunks).
     */
    public static final String[] CHUNK_KEYS = {
        "DataVersion", "Level", "Status",
        "xPos", "yPos", "zPos",
        "LastUpdate", "InhabitedTime",
        "sections", "palette", "block_states",
        "BlockLight", "SkyLight", "Y",
        "Heightmaps", "Biomes", "Structures", "PostProcessing",
        "block_entities", "TileEntities", "Entities",
        "block_ticks", "fluid_ticks", "TileTicks", "LiquidTicks",
        "isLightOn"
    };

    // ------------------------------------------------------------------
    // Hypixel SkyBlock-specific ExtraAttributes vocabulary
    // ------------------------------------------------------------------

    /**
     * Keys from the SkyBlock-specific {@code ExtraAttributes} subcompound on item tags. These are
     * non-vanilla but appear on effectively every SkyBlock item NBT read, so caching them pays off
     * disproportionately relative to list size.
     */
    public static final String[] SKYBLOCK_KEYS = {
        "ExtraAttributes",
        "hot_potato_count", "rarity_upgrades",
        "dungeon_item_level", "upgrade_level",
        "modifier", "timestamp", "stars", "gems",
        "anvil_uses",
        "uuid" // lowercase SkyBlock ExtraAttributes variant; vanilla uses uppercase UUID
    };

    /**
     * Indexed by key length for {@code O(1)} bucket narrowing. {@code BY_LENGTH[n]} is either
     * {@code null} (no known keys of length n) or an array of ASCII-only candidate strings.
     */
    private static final String[][] BY_LENGTH;

    static {
        // Dedupe across categories into an insertion-ordered set.
        java.util.LinkedHashSet<String> unique = new java.util.LinkedHashSet<>(256);
        java.util.Collections.addAll(unique, ITEM_KEYS);
        java.util.Collections.addAll(unique, ENTITY_KEYS);
        java.util.Collections.addAll(unique, BLOCK_ENTITY_KEYS);
        java.util.Collections.addAll(unique, PLAYER_KEYS);
        java.util.Collections.addAll(unique, CHUNK_KEYS);
        java.util.Collections.addAll(unique, SKYBLOCK_KEYS);

        // Validate ASCII-only invariant and compute the maximum length we need to index.
        int maxLen = 0;
        for (String key : unique) {
            for (int i = 0; i < key.length(); i++) {
                if (key.charAt(i) > 0x7F)
                    throw new AssertionError("NbtKnownKeys entries must be ASCII: " + key);
            }

            if (key.length() > maxLen)
                maxLen = key.length();
        }

        // Bucket by length.
        int[] counts = new int[maxLen + 1];
        for (String key : unique) counts[key.length()]++;

        String[][] buckets = new String[maxLen + 1][];
        for (int i = 0; i <= maxLen; i++) {
            if (counts[i] > 0)
                buckets[i] = new String[counts[i]];
        }

        int[] fillIndex = new int[maxLen + 1];
        for (String key : unique) {
            int len = key.length();
            buckets[len][fillIndex[len]++] = key;
        }

        BY_LENGTH = buckets;
    }

    /**
     * Attempts to match the byte range {@code buffer[offset..offset+length)} against the known-key
     * table. Returns the canonical interned {@link String} on a hit so the caller can skip the
     * {@code new String(bytes, charset)} allocation entirely. Returns {@code null} on a miss.
     *
     * <p>The match is byte-for-byte against each candidate's ASCII code units. Valid UTF-8 bytes
     * in the ASCII range {@code [0x01..0x7F]} have the same byte value under both modified and
     * standard UTF-8 encodings, so a hit is correct regardless of which encoding the input uses.
     * Any non-ASCII input byte (high bit set) cannot match any candidate and the method returns
     * {@code null}, which correctly defers to the general UTF-8 decoder.</p>
     */
    public static @Nullable String match(byte[] buffer, int offset, int length) {
        if (length <= 0 || length >= BY_LENGTH.length)
            return null;

        String[] bucket = BY_LENGTH[length];

        if (bucket == null)
            return null;

        candidateLoop:
        for (String candidate : bucket) {
            for (int i = 0; i < length; i++) {
                if (buffer[offset + i] != (byte) candidate.charAt(i))
                    continue candidateLoop;
            }

            return candidate;
        }

        return null;
    }

}
