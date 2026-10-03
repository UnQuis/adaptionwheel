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
    public static void nothingIsEverFree(GameTestHelper helper) {
        // The floor is one level, and it is load-bearing: a price that could reach zero would make
        // the stone a place to stand rather than a trade, which is the thing it stopped being. The
        // cheapest thing in the mod -- an environment, a movement discomfort -- still costs a level.
        String[] cheapestPossible = {
                Concepts.ENV_LIQUID, Concepts.ENV_VOID, Concepts.MOVE_HONEY,
                Concepts.DEBUFF_PREFIX + "levitation", Concepts.COMBAT_SHIELD_LOCK,
                Concepts.PERCEP_STEADY_GAZE,
        };
        for (String concept : cheapestPossible) {
            int price = DomainExchange.priceFor(concept);
            helper.assertTrue(price >= 1,
                    concept + " costs " + price + " -- an exchange must never be free");
        }
        // And the whole ladder, because "1 for easy, 4 for a boss" is the design and a rule that
        // silently collapses to one price is not a ladder.
        helper.assertTrue(DomainExchange.priceFor(Concepts.ENV_LIQUID) == 1,
                "a one-time adaptation is the cheapest thing there is");
        helper.assertTrue(DomainExchange.priceFor("Type_FIRE") == 2,
                "a leveled adaptation costs more than a one-time");
        helper.assertTrue(DomainExchange.priceFor("Drop_NPC_minecraft:zombie") == 3,
                "what a mob drops is worth more than a damage type");
        helper.assertTrue(DomainExchange.priceFor("Existence_minecraft:ender_dragon") == 4,
                "a boss is the most expensive thing on offer");
        helper.assertTrue(DomainExchange.priceFor("Mutation_Thermal") == 4,
                "a milestone is priced with the bosses");
        helper.assertTrue(DomainExchange.priceFor(null) == 0,
                "no concept, no price -- and 0 must not leak into an exchange with nothing selected");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theItemIsAPriceAndNotJustAKey(GameTestHelper helper) {
        // The item narrows the list AND is consumed, so it is a price rather than a key. A recipe
        // that cost nothing would reintroduce the free handout the exchange was built to remove.
        for (Item item : DomainExchange.offerings()) {
            DomainExchange.Recipe recipe = DomainExchange.recipeFor(item);
            helper.assertTrue(recipe.itemsPerTrade() >= 1,
                    item + " costs nothing per trade; an exchange must always cost something");
        }
        // And the rates are ordered by how much the thing is worth: a feather for one level of
        // adaptation is not the same as four nether stars for one.
        helper.assertTrue(DomainExchange.recipeFor(Items.FEATHER).itemsPerTrade() == 1,
                "the feather is the cheapest offering and must say so");
        helper.assertTrue(DomainExchange.recipeFor(Items.NETHER_STAR).itemsPerTrade()
                        > DomainExchange.recipeFor(Items.FEATHER).itemsPerTrade(),
                "the catch-all, which offers all sixteen damage types, must cost more than a feather"
                        + " that offers one");
        // A recipe registered with a nonsensical rate is clamped rather than becoming free.
        DomainExchange.register(Items.REDSTONE, 0, "Type_FIRE");
        try {
            helper.assertTrue(DomainExchange.recipeFor(Items.REDSTONE).itemsPerTrade() == 1,
                    "a zero rate must clamp to one, not make the trade free");
        } finally {
            DomainExchange.unregister(Items.REDSTONE);
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theDeapestPriceTracksThePool(GameTestHelper helper) {
        // The screen scales its bar against this, so it has to be the maximum over what is actually
        // on offer rather than a constant -- a pool of one-time adaptations must not be scaled as
        // though a boss were in it.
        List<String> cheap = DomainExchange.candidates(empty(), 0,
                DomainExchange.recipeFor(Items.FEATHER));
        helper.assertTrue(DomainExchange.dearestPrice(cheap) == 1,
                "levitation is one level and must read as one");
        List<String> broad = DomainExchange.candidates(empty(), 0,
                DomainExchange.recipeFor(Items.NETHER_STAR));
        helper.assertTrue(DomainExchange.dearestPrice(broad) == 2,
                "damage types are two levels and must read as two");
        helper.assertTrue(DomainExchange.dearestPrice(List.of()) == 0,
                "an empty pool has nothing to scale against");
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
        ru.adaptionwheel.menu.ResonanceAltarMenu menu = new ru.adaptionwheel.menu.ResonanceAltarMenu(
                1, inventory, net.minecraft.core.BlockPos.ZERO);

        int size = menu.slots.size();
        helper.assertTrue(size == 38,
                "two input slots plus a 27+9 player grid, got " + size);

        helper.assertTrue(menu.slots.get(ru.adaptionwheel.menu.TradeMenu.OFFER_SLOT).x
                        == ru.adaptionwheel.menu.TradeMenu.OFFER_X
                        && menu.slots.get(ru.adaptionwheel.menu.TradeMenu.OFFER_SLOT).y
                        == ru.adaptionwheel.menu.TradeMenu.OFFER_Y,
                "the offering slot must sit where the screen draws its well, or the item renders"
                        + " outside its own square");

        helper.assertTrue(menu.slots.get(ru.adaptionwheel.menu.TradeMenu.WHEEL_SLOT).x
                        == ru.adaptionwheel.menu.TradeMenu.WHEEL_X
                        && menu.slots.get(ru.adaptionwheel.menu.TradeMenu.WHEEL_SLOT).y
                        == ru.adaptionwheel.menu.TradeMenu.WHEEL_Y,
                "the wheel slot must sit where the screen draws its well and its cross");

        // The last slot must be the last hotbar square, so no bound can be written past the end.
        helper.assertTrue(menu.slots.get(size - 1).y
                        == ru.adaptionwheel.menu.TradeMenu.INV_Y + 58,
                "the hotbar must end at inventory Y plus 58; anything else means a quick-move"
                        + " bound is about to index past the slot list");

        helper.assertTrue(ru.adaptionwheel.menu.TradeMenu.BUTTON_Y + ru.adaptionwheel.menu.TradeMenu.BUTTON_H
                        <= ru.adaptionwheel.menu.TradeMenu.INV_Y,
                "the custom controls must finish above the player's own inventory, or they are drawn"
                        + " underneath it");
        helper.assertTrue(ru.adaptionwheel.menu.TradeMenu.LIST_Y + ru.adaptionwheel.menu.TradeMenu.LIST_H
                        <= ru.adaptionwheel.menu.TradeMenu.BUTTON_Y,
                "the candidate list must finish above the button");

        // Only a wheel belongs in the wheel slot, and the offer slot takes anything: a slot that
        // refuses the item a player is holding is a worse thing to debug than an empty list.
        //
        // mayPlace is the predicate, not the enforcement -- AbstractContainerMenu.clicked consults
        // it at lines 333/429/464, so this asserts the rule the menu actually applies rather than
        // pretending the slot checks by itself.
        net.minecraft.world.item.ItemStack dirt =
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT);
        helper.assertTrue(menu.slots.get(ru.adaptionwheel.menu.TradeMenu.OFFER_SLOT).mayPlace(dirt),
                "the offering slot must accept any stack; what it buys is decided by the recipe");
        helper.assertFalse(menu.slots.get(ru.adaptionwheel.menu.TradeMenu.WHEEL_SLOT).mayPlace(dirt),
                "the wheel slot must reject anything that is not a wheel");
        helper.assertTrue(menu.slots.get(ru.adaptionwheel.menu.TradeMenu.WHEEL_SLOT).mayPlace(
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