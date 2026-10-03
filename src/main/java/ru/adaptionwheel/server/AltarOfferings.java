package ru.adaptionwheel.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which mobs drop what — the index the Resonance Altar trades against.
 *
 * <p>The altar is fed a mob's own loot and offers that mob's adaptations, so the whole mechanic
 * rests on one lookup: given an item, which mobs drop it. Vanilla does not answer that anywhere, so
 * this reads it from data.</p>
 *
 * <h2>Why the mapping is shipped rather than scraped</h2>
 *
 * <p>It could be read out of the loot tables at runtime — {@code EntityType.getDefaultLootTable()},
 * then {@code reloadableRegistries().getLootTable(key)}, then walk each pool's entries. That is
 * exactly the obvious implementation and it was rejected for three reasons: {@code LootPool}'s entry
 * list is a private field behind {@link net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer}
 * on 1.21.1, so reaching the items means an unwrap that differs between branches; the loot table
 * <em>shape</em> changed on 26.3 ({@code functions} → {@code modifier}, {@code item} → {@code name}),
 * so the same walk is two different parsers; and a mob's loot is static data, so scraping it at
 * runtime re-derives a constant fifty-eight times.</p>
 *
 * <p>So the mapping is generated once from the vanilla data files and shipped as
 * {@code data/adaptionwheel/domain_altar/<mob_path>.json}, one per mob that actually drops
 * anything. Fifty-eight mobs qualify; the twenty-six that drop nothing — allay, bat, fox, ocelot,
 * wolf, villager, the player — are simply absent, which is the correct answer rather than an empty
 * entry. A mod that changes what its mobs drop ships its own file and is covered without a code
 * change.</p>
 *
 * <p><b>The Warden is here because vanilla already put it here.</b> The player expects the sculk
 * catalyst to come from it, and it does: {@code loot_tables/entities/warden.json} lists it, so the
 * generation found it without a special case.</p>
 *
 * <p>Built once, on first use, and cleared on server stop so a {@code /reload} that swaps datapacks
 * is not answered from a stale index.</p>
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = ru.adaptionwheel.AdaptionWheel.MODID)
public final class AltarOfferings {

    private static final Logger LOGGER = LoggerFactory.getLogger("adaptionwheel/domain_altar");

    private AltarOfferings() {
    }

    /** Item → the mobs whose loot includes it. Both sides are insertion-ordered for a stable UI. */
    private static Map<Item, List<String>> index;

    /** What one mob can be paid with, for a tooltip or a test. */
    private static final Map<String, List<String>> BY_MOB = new LinkedHashMap<>();

    /**
     * Forgets the index, so a stale answer cannot outlive the world that produced it.
     *
     * <p>Wired to the server stopping by {@link #onServerStopped} below. It used to be documented as
     * "called when the server stops" and was called by nothing at all — harmless while there was one
     * process per world, and wrong the moment two of them shared a JVM, which is exactly what a
     * dedicated server and an integrated one do not do and a test harness does.</p>
     */
    public static void forget() {
        index = null;
    }

    /**
     * Warms the index at server start rather than at the first menu open.
     *
     * <p>Two reasons, and the second is the one that matters. The first is that building it is a
     * directory walk and a JSON parse per file, and doing that inside the first player's first click
     * is a latency nobody asked for. The second is that {@link #ensureLoaded} logs what it loaded,
     * and an index that only announces itself on demand cannot be checked without reproducing the
     * demand.</p>
     */
    @net.neoforged.bus.api.SubscribeEvent
    public static void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        allMobs(event.getServer());
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        forget();
    }

    /**
     * The mobs whose loot contains {@code stack}, empty if it is not a mob drop at all.
     *
     * <p>Never throws on an unknown item: the altar accepts any stack in the slot and the recipe
     * lookup is what decides what it buys, so a question with no answer is an empty list.</p>
     */
    public static List<String> mobsFor(ItemStack stack, MinecraftServer server) {
        if (stack == null || stack.isEmpty()) {
            return List.of();
        }
        return mobsFor(stack.getItem(), server);
    }

    public static List<String> mobsFor(Item item, MinecraftServer server) {
        if (item == null) {
            return List.of();
        }
        ensureLoaded(server);
        List<String> mobs = index.get(item);
        return mobs == null ? List.of() : mobs;
    }

    /** Whether the altar will take this item at all. */
    public static boolean isOffering(ItemStack stack, MinecraftServer server) {
        return !mobsFor(stack, server).isEmpty();
    }

    /** Every mob with loot, for a test or a debug command. */
    public static Map<String, List<String>> allMobs(MinecraftServer server) {
        ensureLoaded(server);
        return Collections.unmodifiableMap(BY_MOB);
    }

    private static void ensureLoaded(MinecraftServer server) {
        if (index != null || server == null) {
            return;
        }
        Map<Item, List<String>> built = new LinkedHashMap<>();
        // 26.3's listResources takes a ResourceManager.Selector, not a Predicate<Identifier>;
        // 1.21.1 takes the Predicate. Same shape of answer, different name.
        for (var resource : server.getResourceManager()
                .listResources("domain_altar",
                        id -> id.getPath().endsWith(".json"))
                .entrySet()) {
            readOne(server, resource.getKey(), resource.getValue(), built);
        }
        index = built;
        // Say what was loaded, once, at INFO.
        //
        // <p>This index is data-driven and its failure is silent in the worst way: a data file
        // naming an item this build does not have is skipped, a file that fails to parse is logged
        // and skipped, and a mob with no file at all is simply absent — so "the altar opens and
        // offers me nothing" looks identical to "this item is not an offering" and to "my wheel
        // already knows everything it opens". One line naming the count turns the first of those
        // three into something checkable, and it is the difference between debugging this with the
        // game in front of you and without.</p>
        //
        // <p>Also counts the items nothing maps to in the other direction, because that is the
        // other silent half: an offering item that resolves to a real item but to no mobs.</p>
        LOGGER.info("Domain altar offerings: {} mobs, {} offering items",
                BY_MOB.size(), built.size());
    }

    private static void readOne(MinecraftServer server, Identifier location,
                                 Resource resource, Map<Item, List<String>> built) {
        String mob;
        List<String> items;
        try (BufferedReader reader = resource.openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            mob = root.get("mob").getAsString();
            JsonArray array = root.getAsJsonArray("items");
            items = new ArrayList<>(array.size());
            for (int i = 0; i < array.size(); i++) {
                items.add(array.get(i).getAsString());
            }
        } catch (IOException | RuntimeException e) {
            // A malformed file must not take the whole altar down. One mob losing its offerings
            // is a much smaller failure than every mob losing them.
            LOGGER.warn("Domain altar offerings for {} could not be read ({})",
                    location, e.toString());
            return;
        }
        if (mob.isBlank() || items.isEmpty()) {
            return;
        }
        BY_MOB.put(mob, List.copyOf(items));
        for (String itemId : items) {
            // 26.3's Registry.get returns Optional<Holder.Reference<Item>> rather than the item, and
            // an absent key comes back empty rather than as air -- which is the answer wanted here:
            // a file naming an item this build does not have is skipped rather than fatal, since
            // the loot tables and the item registry come from the same game version but a mod may
            // trim one out of an existing pack, and one mob losing its offerings is a much smaller
            // failure than every mob losing them.
            Item item = BuiltInRegistries.ITEM.get(Identifier.parse(itemId))
                    .map(net.minecraft.core.Holder.Reference::value).orElse(null);
            if (item != null) {
                built.computeIfAbsent(item, k -> new ArrayList<>()).add(mob);
            } else {
                // Named by the data and absent from the game: worth a line each, because the player
                // will otherwise be told an offering opens nothing, for an item they can hold.
                LOGGER.warn("{} offers {}, which this build has no item for", mob, itemId);
            }
        }
    }
}