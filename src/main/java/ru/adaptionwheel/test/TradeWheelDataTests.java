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

@GameTestHolder(AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class TradeWheelDataTests {

    private static final List<String> EXISTING = List.of(
            "Type_Fire", "Env_Lava", "Contact_minecraft:zombie", "Offense_NPC_minecraft:skeleton");

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

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theStateATradeReadsComesOffTheStack(GameTestHelper helper) {
        PlayerAdaption onWheel = progressed();
        ItemStack stack = wheel(onWheel);

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

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void anEmptySlotIsAnEmptyState(GameTestHelper helper) {
        PlayerAdaption read = AdaptionEvents.readFrom(ItemStack.EMPTY);
        helper.assertTrue(read.getAdaptCount() == 0, "nothing fed, nothing adapted");
        helper.assertTrue(read.level("Type_Fire") == 0, "and no levels invented out of thin air");

        PlayerAdaption bare = AdaptionEvents.readFrom(new ItemStack(ModItems.MAHORAGA_WHEEL.get()));
        helper.assertTrue(bare.getAdaptCount() == 0, "a fresh wheel starts empty, not corrupt");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void equippingKeepsWhatThePlayerAlreadyHad(GameTestHelper helper) {
        PlayerAdaption attachment = new PlayerAdaption();
        attachment.levels.put("Type_Fire", PlayerAdaption.MAX_LEVEL);
        attachment.adapted.add("Mutation_Flight");
        attachment.adapted.add("Debuff_minecraft:levitation");
        attachment.killCounts.put("Drop_NPC_minecraft:chicken", 12);
        attachment.invalidateAdaptCount();

        PlayerAdaption onWheel = new PlayerAdaption();
        onWheel.levels.put("Contact_minecraft:phantom", PlayerAdaption.MAX_LEVEL);
        onWheel.adapted.add("Env_Void");
        onWheel.invalidateAdaptCount();
        ru.adaptionwheel.data.WheelData.fromPlayer(onWheel).loadInto(attachment);

        helper.assertTrue(attachment.level("Type_Fire") == PlayerAdaption.MAX_LEVEL,
                "a grant made while the wheel was off must survive equipping it; loadInto used to clear"
                        + " every collection first, which silently threw those adaptations away");
        helper.assertTrue(attachment.isAdapted("Mutation_Flight"),
                "and Mutation_Flight with them, which is why Flight never took effect");
        helper.assertTrue(attachment.isAdapted("Debuff_minecraft:levitation"),
                "and the levitation adaptation, the third of Flight's conditions");
        helper.assertTrue(attachment.kills("Drop_NPC_minecraft:chicken") == 12,
                "and the kill counts, which a Drop_NPC_ level is derived from");
        helper.assertTrue(attachment.level("Contact_minecraft:phantom") == PlayerAdaption.MAX_LEVEL,
                "while what the wheel itself carried comes across too");
        helper.assertTrue(attachment.isAdapted("Env_Void"),
                "including its one-time adaptations");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void loadingNeverLowersAConceptOrDuplicatesATask(GameTestHelper helper) {
        PlayerAdaption attachment = new PlayerAdaption();
        attachment.levels.put("Type_Fire", 7);
        attachment.killCounts.put("Drop_NPC_minecraft:zombie", 30);
        attachment.tasks.add(new ru.adaptionwheel.data.AdaptionTask("Env_Lava", 40, 100));
        attachment.invalidateAdaptCount();

        PlayerAdaption weaker = new PlayerAdaption();
        weaker.levels.put("Type_Fire", 2);
        weaker.killCounts.put("Drop_NPC_minecraft:zombie", 5);
        weaker.tasks.add(new ru.adaptionwheel.data.AdaptionTask("Env_Lava", 10, 100));
        weaker.tasks.add(new ru.adaptionwheel.data.AdaptionTask("Env_Void", 10, 100));
        weaker.invalidateAdaptCount();
        ru.adaptionwheel.data.WheelData.fromPlayer(weaker).loadInto(attachment);

        helper.assertTrue(attachment.level("Type_Fire") == 7,
                "a merge takes the higher level, so handing a wheel over cannot walk a concept backwards,"
                        + " got " + attachment.level("Type_Fire"));
        helper.assertTrue(attachment.kills("Drop_NPC_minecraft:zombie") == 30,
                "kill counts must not fall either, or a Drop_NPC_ adaptation would regress");
        int envLava = 0;
        int total = 0;
        for (ru.adaptionwheel.data.AdaptionTask task : attachment.tasks) {
            if (task.concept.equals("Env_Lava")) {
                envLava++;
            }
            total++;
        }
        helper.assertTrue(envLava == 1,
                "one analysis per concept, or the same task completes twice and pays out twice;"
                        + " found " + envLava);
        helper.assertTrue(total == 2, "and the task the wheel carried is still added, got " + total);
        helper.succeed();
    }

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
