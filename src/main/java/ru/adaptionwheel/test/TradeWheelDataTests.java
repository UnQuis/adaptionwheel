package ru.adaptionwheel.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.item.ModItems;
import ru.adaptionwheel.server.AdaptionEvents;

import java.util.List;

/**
 * The fed wheel's own data, which is what a trade writes to.
 *
 * <p>This is a regression suite for a reported bug: buying an adaptation at the Domain Stone or the
 * Resonance Altar wiped everything else off the wheel. The cause was that the trade granted into
 * the player's attachment and wrote <em>that</em> onto the fed stack — and the attachment belongs to
 * the wheel the player took off to put this one in, which unequipping empties. So the purchase
 * replaced a wheel's fifty adaptations with the one just bought.</p>
 *
 * <p>What is asserted here is the half that can be reached without a player: that the state a trade
 * reads and writes comes off the <em>stack</em>, and that writing it back leaves the rest of that
 * wheel alone. The exchange path itself needs a {@code ServerPlayer}, and a mock login is broken
 * while Curios is installed, so what is not covered here is {@code TradeMenu.exchange()} — which is
 * why the fix was made by construction rather than by patching one call site: the trade has no way
 * to reach the player's attachment at all now.</p>
 */
@GameTestHolder(AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class TradeWheelDataTests {

    private static final List<String> EXISTING = List.of(
            "Type_Fire", "Env_Lava", "Contact_minecraft:zombie", "Offense_NPC_minecraft:skeleton");

    /** A wheel carrying some history, which is what a player would actually bring to a block. */
    private static PlayerAdaption progressed() {
        PlayerAdaption data = new PlayerAdaption();
        for (String concept : EXISTING) {
            data.levels.put(concept, PlayerAdaption.MAX_LEVEL);
        }
        data.adapted.add("Env_Void");
        data.killCounts.put("Drop_NPC_minecraft:chicken", 40);
        data.invalidateAdaptCount();
        return data;
    }

    private static ItemStack wheel(PlayerAdaption data) {
        ItemStack stack = new ItemStack(ModItems.MAHORAGA_WHEEL.get());
        AdaptionEvents.saveToStack(stack, data);
        return stack;
    }

    /** The load-bearing one: the state a trade reads is the wheel's own, not anybody's attachment. */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theStateATradeReadsComesOffTheStack(GameTestHelper helper) {
        PlayerAdaption onWheel = progressed();
        ItemStack stack = wheel(onWheel);

        // Deliberately NOT the player's own state: the bug was that a trade read this instead.
        PlayerAdaption elsewhere = new PlayerAdaption();

        PlayerAdaption read = AdaptionEvents.readFrom(stack);
        helper.assertTrue(read.level("Type_Fire") == PlayerAdaption.MAX_LEVEL,
                "a fed wheel must report its own Type_Fire, got " + read.level("Type_Fire"));
        helper.assertTrue(read.level("Contact_minecraft:zombie") == PlayerAdaption.MAX_LEVEL,
                "and its own Contact_minecraft:zombie");
        helper.assertTrue(read.isAdapted("Env_Void"), "and its own one-time adaptations");
        helper.assertTrue(read.kills("Drop_NPC_minecraft:chicken") == 40,
                "and its own kill counts, which is what a drop-rate adaptation is derived from");
        helper.assertTrue(read != elsewhere,
                "readFrom must hand back a detached state; sharing one would put two wheels' data"
                        + " in the same object");
        helper.succeed();
    }

    /**
     * The reported symptom, as a pure round trip: buy one thing, keep everything else.
     *
     * <p>Stands in for {@code TradeMenu.exchange()}, which cannot be built without a player. What it
     * pins is that adding one adaptation to the fed state and writing it back does not drop any of
     * the wheel's existing ones — the exact shape of the loss that was reported.</p>
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void buyingOneKeepsTheRestOfTheWheel(GameTestHelper helper) {
        ItemStack stack = wheel(progressed());

        PlayerAdaption fed = AdaptionEvents.readFrom(stack);
        fed.levels.put("Type_Starve", PlayerAdaption.MAX_LEVEL);
        AdaptionEvents.saveToStack(stack, fed);

        PlayerAdaption after = AdaptionEvents.readFrom(stack);
        helper.assertTrue(after.level("Type_Starve") == PlayerAdaption.MAX_LEVEL,
                "the adaptation just bought must be on the wheel");
        for (String concept : EXISTING) {
            helper.assertTrue(after.level(concept) == PlayerAdaption.MAX_LEVEL,
                    concept + " was on the wheel before the purchase and must still be there;"
                            + " losing it was the reported bug");
        }
        helper.assertTrue(after.isAdapted("Env_Void"),
                "and the one-time adaptations survive too, not just the leveled ones");
        helper.assertTrue(after.kills("Drop_NPC_minecraft:chicken") == 40,
                "and the kill counts, since a drop-rate adaptation is derived from them");
        helper.assertTrue(after.getAdaptCount() == EXISTING.size() + 2,
                "the wheel's adapt count must have grown by exactly one, got " + after.getAdaptCount());
        helper.succeed();
    }

    /** An empty slot is an empty state, not a failure: the list still has to show what is on sale. */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void anEmptySlotIsAnEmptyState(GameTestHelper helper) {
        PlayerAdaption read = AdaptionEvents.readFrom(ItemStack.EMPTY);
        helper.assertTrue(read.getAdaptCount() == 0, "nothing fed, nothing adapted");
        helper.assertTrue(read.level("Type_Fire") == 0, "and no levels invented out of thin air");

        // A wheel with no data component at all is the same answer: a brand new wheel is not a
        // broken one, and must not be treated as a reason to refuse the screen.
        PlayerAdaption bare = AdaptionEvents.readFrom(new ItemStack(ModItems.MAHORAGA_WHEEL.get()));
        helper.assertTrue(bare.getAdaptCount() == 0, "a fresh wheel starts empty, not corrupt");
        helper.succeed();
    }

    /**
     * A drop-rate adaptation is bought in kills, so the count has to move with it.
     *
     * <p>Pins the arithmetic {@code grantToWheel} does before the grant: the level a purchase sets
     * must equal what the existing kill-count table derives, never an independent number.</p>
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theKillsADropPurchasePaysForAreTheOnesTheTableAsksFor(GameTestHelper helper) {
        int max = PlayerAdaption.MAX_LEVEL;
        int wanted = (int) Math.ceil(ru.adaptionwheel.config.AdaptionConfig.lootKills(max));
        helper.assertTrue(wanted > 0, "maxing a drop-rate adaptation must cost some kills, got " + wanted);

        PlayerAdaption fed = AdaptionEvents.readFrom(ItemStack.EMPTY);
        fed.killCounts.merge("Drop_NPC_minecraft:zombie", wanted, Integer::sum);
        helper.assertTrue(ru.adaptionwheel.config.AdaptionConfig.dropLevelFromKills(fed.kills(
                        "Drop_NPC_minecraft:zombie")) == max,
                "paying those kills must reach max through the ordinary rule, or the altar would be"
                        + " inventing a second way to reach the same number");
        helper.succeed();
    }
}
