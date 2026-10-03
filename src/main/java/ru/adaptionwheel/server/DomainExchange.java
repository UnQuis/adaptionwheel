package ru.adaptionwheel.server;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.adaptionwheel.adapt.AdaptationDefinition;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.adapt.AdaptationDomain;
import ru.adaptionwheel.adapt.AdaptationRegistry;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.category.WheelTier;
import ru.adaptionwheel.data.PlayerAdaption;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DomainExchange {

    private DomainExchange() {
    }

    public record Selector(String text) {

        public static Selector of(String text) {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("a domain exchange selector cannot be blank");
            }
            return new Selector(text);
        }

        public boolean matches(String concept) {
            return isExact() ? concept.equals(text) : concept.startsWith(prefix());
        }

        public boolean isExact() {
            return !text.endsWith("*");
        }

        public String prefix() {
            return text.substring(0, text.length() - 1);
        }
    }

    public record Recipe(Item item, List<Selector> selectors, int itemsPerTrade) {

        public Recipe {
            itemsPerTrade = Math.max(1, itemsPerTrade);
        }
    }

    private static final Map<Item, Recipe> BY_ITEM = new LinkedHashMap<>();

    public static void bootstrap() {
        if (!BY_ITEM.isEmpty()) {
            return;
        }

        register(Items.FEATHER, 1, "Debuff_levitation");
        register(Items.ENDER_EYE, 1, "Env_Void");
        register(Items.ENDER_PEARL, 1, "Env_Void");

        register(Items.WATER_BUCKET, 1, "Env_Liquid");
        register(Items.LAVA_BUCKET, 2, "Env_Lava");
        register(Items.COD, 1, "Env_Drowning");
        register(Items.SLIME_BALL, 1, "Env_Slime");
        register(Items.COBWEB, 1, "Env_Cobweb");
        register(Items.PACKED_ICE, 1, "Env_Ice");
        register(Items.BONE, 1, "Env_Suffocate", "Type_SUFFOCATE");

        register(Items.HONEY_BOTTLE, 1, "Move_Honey");
        register(Items.SNOWBALL, 1, "Move_PowderSnow");
        register(Items.SWEET_BERRIES, 1, "Move_BerryBush");
        register(Items.SOUL_SAND, 1, "Move_SoulSand");

        register(Items.GHAST_TEAR, 1, "Debuff_wither");

        register(Items.NETHER_WART, 1, "Type_WITHER");
        register(Items.BLAZE_POWDER, 2, "Type_FIRE");
        register(Items.SNOW_BLOCK, 2, "Type_FREEZE");

        register(Items.NETHER_STAR, 4, "Type_*");
    }

    public static void register(Item item, int itemsPerTrade, String... selectors) {
        List<Selector> parsed = new ArrayList<>(selectors.length);
        for (String text : selectors) {
            parsed.add(Selector.of(text));
        }
        BY_ITEM.put(item, new Recipe(item, List.copyOf(parsed), itemsPerTrade));
    }

    public static void unregister(Item item) {
        BY_ITEM.remove(item);
    }

    public static Recipe recipeFor(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : BY_ITEM.get(stack.getItem());
    }

    public static Recipe recipeFor(Item item) {
        return BY_ITEM.get(item);
    }

    public static List<Item> offerings() {
        return List.copyOf(BY_ITEM.keySet());
    }

    public static List<String> candidates(PlayerAdaption data, int tier, Recipe recipe) {
        if (recipe == null) {
            return List.of();
        }
        List<String> pool = new ArrayList<>();
        for (Selector selector : recipe.selectors()) {
            if (selector.isExact()) {
                if (!isFinished(data, selector.text())) {
                    pool.add(selector.text());
                }
                continue;
            }
            for (AdaptationDefinition definition : AdaptationRegistry.allDefinitions()) {
                String concept = definition.concept();
                if (selector.matches(concept) && !isFinished(data, concept) && !pool.contains(concept)) {
                    pool.add(concept);
                }
            }
        }
        pool.remove(Concepts.ADVERSITY);
        pool.sort(orderingFor(tier));
        return List.copyOf(pool);
    }

    public static boolean isFinished(PlayerAdaption data, String concept) {
        return data.isAdapted(concept) || data.level(concept) > 0;
    }

    private static Comparator<String> orderingFor(int tier) {
        return Comparator
                .<String>comparingInt(concept -> WheelTier.familyUnlocked(concept, tier) ? 0 : 1)
                .thenComparing(DomainExchange::domainNameOf)
                .thenComparing(Comparator.naturalOrder());
    }

    private static String domainNameOf(String concept) {
        AdaptationDefinition definition = AdaptationRegistry.get(concept);
        return definition == null ? "" : definition.domain().name();
    }

    public static int priceFor(String concept) {
        if (concept == null) {
            return 0;
        }
        if (concept.startsWith(Concepts.EXISTENCE_PREFIX)
                || concept.startsWith(Concepts.MUTATION_PREFIX)
                || concept.equals(Concepts.DIMENSION_DESTROY)) {
            return 4;
        }
        if (concept.startsWith(Concepts.DROP_PREFIX)) {
            return 3;
        }
        return Concepts.isLevelBased(concept) ? 2 : 1;
    }

    public static int priceForLevel(String concept, int heldLevel) {
        return grow(priceFor(concept), heldLevel, AdaptionConfig.TRADE_MAX_XP.get());
    }

    public static int itemsForLevel(int baseItems, int heldLevel) {
        return grow(baseItems, heldLevel, AdaptionConfig.TRADE_MAX_ITEMS.get());
    }

    private static int grow(int base, int heldLevel, int cap) {
        if (base <= 0) {
            return 0;
        }
        double factor = Math.max(1.0D, AdaptionConfig.TRADE_COST_GROWTH.get());
        int price = base;
        for (int level = 0; level < heldLevel && price < cap; level++) {
            price = Math.min(cap, (int) Math.ceil(price * factor));
        }
        return Math.min(price, cap);
    }

    public static int dearestPrice(List<String> pool) {
        int dearest = 0;
        for (String concept : pool) {
            dearest = Math.max(dearest, priceFor(concept));
        }
        return dearest;
    }

    public static AdaptationDomain domainOf(String concept) {
        AdaptationDefinition definition = AdaptationRegistry.get(concept);
        return definition == null ? AdaptationDomain.SPECIAL : definition.domain();
    }
}
