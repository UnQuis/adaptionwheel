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

@net.neoforged.fml.common.EventBusSubscriber(modid = ru.adaptionwheel.AdaptionWheel.MODID)
public final class AltarOfferings {

    private static final Logger LOGGER = LoggerFactory.getLogger("adaptionwheel/domain_altar");

    private AltarOfferings() {
    }

    private static Map<Item, List<String>> index;

    private static final Map<String, List<String>> BY_MOB = new LinkedHashMap<>();

    public static void forget() {
        index = null;
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        allMobs(event.getServer());
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        forget();
    }

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

    public static boolean isOffering(ItemStack stack, MinecraftServer server) {
        return !mobsFor(stack, server).isEmpty();
    }

    public static Map<String, List<String>> allMobs(MinecraftServer server) {
        ensureLoaded(server);
        return Collections.unmodifiableMap(BY_MOB);
    }

    private static void ensureLoaded(MinecraftServer server) {
        if (index != null || server == null) {
            return;
        }
        Map<Item, List<String>> built = new LinkedHashMap<>();

        for (var resource : server.getResourceManager()
                .listResources("domain_altar",
                        id -> id.getPath().endsWith(".json"))
                .entrySet()) {
            readOne(server, resource.getKey(), resource.getValue(), built);
        }
        index = built;

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

            LOGGER.warn("Domain altar offerings for {} could not be read ({})",
                    location, e.toString());
            return;
        }
        if (mob.isBlank() || items.isEmpty()) {
            return;
        }
        BY_MOB.put(mob, List.copyOf(items));
        for (String itemId : items) {

            Item item = BuiltInRegistries.ITEM.get(Identifier.parse(itemId))
                    .map(net.minecraft.core.Holder.Reference::value).orElse(null);
            if (item != null) {
                built.computeIfAbsent(item, k -> new ArrayList<>()).add(mob);
            } else {

                LOGGER.warn("{} offers {}, which this build has no item for", mob, itemId);
            }
        }
    }
}
