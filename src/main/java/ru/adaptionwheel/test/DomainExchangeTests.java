package ru.adaptionwheel.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.server.DomainExchange;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * The Domain Stone's price list and what it is willing to sell.
 *
 * <p>All of this is pure arithmetic over the registry, so none of it needs a player — which matters
 * more than it sounds, because a gametest that makes a mock player is broken outright while Curios
 * is installed.</p>
 *
 * <p>The test that matters most is {@link #everyRecipeSellsSomething}. A recipe is a list of
 * concept selectors, and a selector that names nothing — a misspelt concept, or a family whose
 * concepts are all minted at runtime and so are not in the registry to be found — produces an empty
 * list. That failure is completely silent: the screen opens, the player puts their item in, and the
 * list is simply blank. It is exactly the "present, registered, and connected to nothing" shape, and
 * nothing else here would catch it.</p>
 */
@GameTestHolder(AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class DomainExchangeTests {

    private static PlayerAdaption empty() {
        return new PlayerAdaption(new HashMap<>(), List.of(), List.of(), List.of(), List.of(),
                new HashMap<>(), new HashMap<>(), 0, 0, 0, false, 0f, 0f, false, 0);
    }

    private static PlayerAdaption holding(String... concepts) {
        PlayerAdaption data = empty();
        for (String concept : concepts) {
            data.adapted.add(concept);
        }
        return data;
    }

    private static PlayerAdaption levelled(String concept, int level) {
        PlayerAdaption data = empty();
        data.levels.put(concept, level);
        return data;
    }

    /** The one that matters: no recipe may be a dead end. */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void everyRecipeSellsSomething(GameTestHelper helper) {
        helper.assertTrue(!DomainExchange.offerings().isEmpty(),
                "the stone was left with no price list, so it would open and show nothing at all");
        for (Item item : DomainExchange.offerings()) {
            DomainExchange.Recipe recipe = DomainExchange.recipeFor(item);
            List<String> pool = DomainExchange.candidates(empty(), 0, recipe);
            helper.assertTrue(!pool.isEmpty(),
                    "the recipe for " + item + " offers nothing at all. Either a selector names a"
                            + " concept that does not exist, or it names a family whose concepts are"
                            + " minted at runtime and so are absent from the registry this walks.");
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void anItemNarrowsRatherThanNames(GameTestHelper helper) {
        DomainExchange.Recipe feather = DomainExchange.recipeFor(Items.FEATHER);
        List<String> levitation = DomainExchange.candidates(empty(), 0, feather);
        // A feather is the example the whole block was sketched around, and it names a concept the
        // registry does not hold -- debuff keys are minted from whichever effect a player is under.
        helper.assertTrue(levitation.contains("Debuff_levitation"),
                "a feather must buy levitation, got " + levitation);

        DomainExchange.Recipe star = DomainExchange.recipeFor(Items.NETHER_STAR);
        List<String> damageTypes = DomainExchange.candidates(empty(), 0, star);
        helper.assertTrue(damageTypes.size() > 1,
                "a nether star narrows to the damage-type family, so it must offer a choice rather"
                        + " than one answer");
        for (String concept : damageTypes) {
            helper.assertTrue(concept.startsWith("Type_"),
                    "a nether star must only ever offer damage types, got " + concept);
        }
        helper.assertTrue(!damageTypes.contains("Debuff_levitation"),
                "a nether star must not leak outside the family it names");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theStoneNeverSellsWhatTheWheelAlreadyHas(GameTestHelper helper) {
        DomainExchange.Recipe star = DomainExchange.recipeFor(Items.NETHER_STAR);
        List<String> all = DomainExchange.candidates(empty(), 0, star);
        String bought = all.get(0);
        helper.assertTrue(!DomainExchange.candidates(holding(bought), 0, star).contains(bought),
                "an adaptation already held must not be on offer -- it would cost items for nothing");
        helper.assertTrue(!DomainExchange.candidates(levelled(bought, 3), 0, star).contains(bought),
                "a partially levelled adaptation must not be on offer either; the stone sells whole"
                        + " concepts, not the next rung");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void oneItemPerLevel(GameTestHelper helper) {
        DomainExchange.Recipe feather = DomainExchange.recipeFor(Items.FEATHER);
        helper.assertTrue(feather.perLevel() == 1,
                "the rule the player chose is one item per level, so a one-per-level recipe is 1");
        helper.assertTrue(feather.costFor(1) == 1, "one level of a one-per-level recipe costs one");
        helper.assertTrue(feather.costFor(8) == 8, "eight levels must cost eight");

        DomainExchange.Recipe star = DomainExchange.recipeFor(Items.NETHER_STAR);
        helper.assertTrue(star.perLevel() >= 4,
                "the catch-all sells all sixteen damage types to eight levels; pricing it like a"
                        + " one-time would make it strictly better than every other recipe");
        helper.assertTrue(star.costFor(8) == 8 * star.perLevel(),
                "cost must be levels times per-level, with no hidden term");

        helper.assertTrue(feather.costFor(0) == 0, "zero levels must cost nothing, not something");
        helper.assertTrue(feather.costFor(-3) == 0,
                "a negative level count must not become a credit");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aOneTimeAdaptationIsWorthOneLevel(GameTestHelper helper) {
        helper.assertTrue(DomainExchange.maxLevelsFor(Concepts.ENV_VOID) == 1,
                "an environment is learned or not learned, so the cap must be one");
        helper.assertTrue(DomainExchange.maxLevelsFor("Type_FIRE") == PlayerAdaption.MAX_LEVEL,
                "a damage type levels to the cap");
        // The one that would cost items for nothing: eight levels requested of a one-time concept.
        helper.assertTrue(DomainExchange.clampLevels(Concepts.ENV_VOID, 8) == 1,
                "asking eight levels of a one-time adaptation must collapse to one rather than"
                        + " quietly charge for eight");
        helper.assertTrue(DomainExchange.clampLevels("Type_FIRE", 99) == PlayerAdaption.MAX_LEVEL,
                "a level count above the cap must come down to the cap");
        helper.assertTrue(DomainExchange.clampLevels("Type_FIRE", 0) == 1,
                "zero levels must clamp up to one, never to zero, or the button would grant nothing"
                        + " for a price");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theStoneNeverSellsAdversity(GameTestHelper helper) {
        // Adversity is a challenge, not an adaptation. Handing it over would start a timed event the
        // player neither asked for nor could have paid for: the price would be an item, and what it
        // buys would be a fight.
        DomainExchange.register(Items.REDSTONE, 1, "ADBERSITY", "Type_FIRE");
        try {
            List<String> pool = DomainExchange.candidates(empty(), 0, DomainExchange.recipeFor(Items.REDSTONE));
            helper.assertTrue(!pool.contains(Concepts.ADVERSITY),
                    "the stone must never be a way to acquire adversity");
            helper.assertTrue(pool.contains("Type_FIRE"),
                    "the rest of that recipe must still sell, or the guard removed too much");
        } finally {
            DomainExchange.unregister(Items.REDSTONE);
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theOrderIsTheDocumentedOne(GameTestHelper helper) {
        // The client picks a row by index, so the pool has to be a function of its contents. Two
        // builds of the same pool in a different order is a stone that grants whatever the server's
        // index happened to point at, which is not what the player clicked.
        DomainExchange.Recipe star = DomainExchange.recipeFor(Items.NETHER_STAR);
        PlayerAdaption data = empty();
        List<String> pool = DomainExchange.candidates(data, 0, star);
        helper.assertTrue(pool.size() > 1, "this needs a pool with more than one entry to mean"
                + " anything");

        // The pool has to come back sorted, not merely deterministic: a registry walk that was left
        // unsorted would still be repeatable, and still would not be the order the row positions on
        // screen were computed against.
        for (int i = 1; i < pool.size(); i++) {
            String previous = pool.get(i - 1);
            String current = pool.get(i);
            helper.assertTrue(compare(previous, current, 0) <= 0,
                    "pool must be sorted, but " + previous + " came before " + current
                            + " out of order");
        }
        helper.assertTrue(!pool.contains(Concepts.ADVERSITY), "and it must never contain adversity");
        helper.succeed();
    }

    /**
     * The documented order: revealed families first, then domain, then concept name. Written out
     * rather than delegated to the production comparator, because a test that asks the thing under
     * test whether it is correct cannot fail.
     */
    private static int compare(String a, String b, int tier) {
        int revealed = Integer.compare(
                ru.adaptionwheel.category.WheelTier.familyUnlocked(b, tier) ? 0 : 1,
                ru.adaptionwheel.category.WheelTier.familyUnlocked(a, tier) ? 0 : 1);
        if (revealed != 0) {
            return revealed;
        }
        int domain = DomainExchange.domainOf(a).name().compareTo(DomainExchange.domainOf(b).name());
        return domain != 0 ? domain : a.compareTo(b);
    }

    /**
     * The menu's shape, and the bug it exists to prevent.
     *
     * <p>Three real faults lived in this area at once and none of them would show up in a build:
     * the slots were placed at x=26 while the screen drew their wells at x=8, so every item
     * rendered outside its own square; shift-clicking a wheel into a full wheel slot indexed slot
     * 38..46 in a 38-slot list, which throws rather than failing quietly; and the player's own grid
     * sat under the screen's custom controls. All three are layout arithmetic, so they are pinned
     * here rather than left to be found by playing.</p>
     *
     * <p>Built with a playerless {@link net.minecraft.world.entity.player.Inventory}, which makes
     * this the <em>client</em> constructor path — a mock server player cannot be used at all while
     * Curios is installed, and this half is what the layout constants are read by.</p>
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theMenuIsLaidOutWhereTheScreenExpects(GameTestHelper helper) {
        // A null owner: the constructor does not read it, and leaving it null is what makes this the
        // client-side path. Player cannot be instantiated for a test at all -- its constructor needs
        // a profile and its abstract methods are a dozen of them -- and a mock server player logs in
        // as far as Curios is concerned, which fails the test before its body runs.
        net.minecraft.world.entity.player.Inventory inventory =
                new net.minecraft.world.entity.player.Inventory(null);
        ru.adaptionwheel.menu.DomainStoneMenu menu = new ru.adaptionwheel.menu.DomainStoneMenu(
                1, inventory, net.minecraft.core.BlockPos.ZERO);

        int size = menu.slots.size();
        helper.assertTrue(size == 38,
                "two input slots plus a 27+9 player grid, got " + size);

        helper.assertTrue(menu.slots.get(ru.adaptionwheel.menu.DomainStoneMenu.OFFER_SLOT).x
                        == ru.adaptionwheel.menu.DomainStoneMenu.OFFER_X
                        && menu.slots.get(ru.adaptionwheel.menu.DomainStoneMenu.OFFER_SLOT).y
                        == ru.adaptionwheel.menu.DomainStoneMenu.OFFER_Y,
                "the offering slot must sit where the screen draws its well, or the item renders"
                        + " outside its own square");

        helper.assertTrue(menu.slots.get(ru.adaptionwheel.menu.DomainStoneMenu.WHEEL_SLOT).x
                        == ru.adaptionwheel.menu.DomainStoneMenu.WHEEL_X
                        && menu.slots.get(ru.adaptionwheel.menu.DomainStoneMenu.WHEEL_SLOT).y
                        == ru.adaptionwheel.menu.DomainStoneMenu.WHEEL_Y,
                "the wheel slot must sit where the screen draws its well and its cross");

        // The last slot must be the last hotbar square, so no bound can be written past the end.
        helper.assertTrue(menu.slots.get(size - 1).y
                        == ru.adaptionwheel.menu.DomainStoneMenu.INV_Y + 58,
                "the hotbar must end at inventory Y plus 58; anything else means a quick-move"
                        + " bound is about to index past the slot list");

        helper.assertTrue(ru.adaptionwheel.menu.DomainStoneMenu.BUTTON_Y + ru.adaptionwheel.menu.DomainStoneMenu.BUTTON_H
                        <= ru.adaptionwheel.menu.DomainStoneMenu.INV_Y,
                "the custom controls must finish above the player's own inventory, or they are drawn"
                        + " underneath it");
        helper.assertTrue(ru.adaptionwheel.menu.DomainStoneMenu.LIST_Y + ru.adaptionwheel.menu.DomainStoneMenu.LIST_H
                        <= ru.adaptionwheel.menu.DomainStoneMenu.BUTTON_Y,
                "the candidate list must finish above the button");

        // Only a wheel belongs in the wheel slot, and the offer slot takes anything: a slot that
        // refuses the item a player is holding is a worse thing to debug than an empty list.
        //
        // mayPlace is the predicate, not the enforcement -- AbstractContainerMenu.clicked consults
        // it at lines 333/429/464, so this asserts the rule the menu actually applies rather than
        // pretending the slot checks by itself.
        net.minecraft.world.item.ItemStack dirt =
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT);
        helper.assertTrue(menu.slots.get(ru.adaptionwheel.menu.DomainStoneMenu.OFFER_SLOT).mayPlace(dirt),
                "the offering slot must accept any stack; what it buys is decided by the recipe");
        helper.assertFalse(menu.slots.get(ru.adaptionwheel.menu.DomainStoneMenu.WHEEL_SLOT).mayPlace(dirt),
                "the wheel slot must reject anything that is not a wheel");
        helper.assertTrue(menu.slots.get(ru.adaptionwheel.menu.DomainStoneMenu.WHEEL_SLOT).mayPlace(
                        new net.minecraft.world.item.ItemStack(ru.adaptionwheel.item.ModItems.MAHORAGA_WHEEL.get())),
                "and must accept the wheel");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aNullRecipeSellsNothing(GameTestHelper helper) {
        helper.assertTrue(DomainExchange.recipeFor(net.minecraft.world.item.ItemStack.EMPTY) == null,
                "an empty slot is not an offering");
        helper.assertTrue(DomainExchange.recipeFor(new net.minecraft.world.item.ItemStack(Items.DIRT)) == null,
                "dirt is not an offering, and must read as null rather than as an empty recipe");
        helper.assertTrue(DomainExchange.candidates(empty(), 0, null).isEmpty(),
                "a null recipe must give an empty pool, not a crash");
        helper.succeed();
    }
}