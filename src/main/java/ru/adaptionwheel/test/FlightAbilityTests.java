package ru.adaptionwheel.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Abilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.server.FlightAbility;

@GameTestHolder(AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class FlightAbilityTests {

    private static Abilities grounded() {
        return new Abilities();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void grantingFlightSetsFlyingAndNotJustMayfly(GameTestHelper helper) {
        Abilities abilities = grounded();
        helper.assertTrue(!abilities.mayfly && !abilities.flying, "vanilla starts with neither");

        helper.assertTrue(FlightAbility.apply(abilities, true), "the first grant changes something");
        helper.assertTrue(abilities.mayfly, "mayfly is set");
        helper.assertTrue(abilities.flying,
                "AND flying is set. mayfly on its own only arms vanilla's double-tap, and the client "
                        + "cancels it again while the player is on the ground, so a mutation that set "
                        + "mayfly alone looked correct and never left the ground. This is the bug.");

        FlightAbility.apply(abilities, false);
        helper.assertTrue(!abilities.mayfly, "turning it off takes mayfly back");
        helper.assertTrue(!abilities.flying, "and flying with it, or a revoked mutation would keep you aloft");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void anUnchangedStateDoesNotAskForAnotherPacket(GameTestHelper helper) {
        Abilities abilities = grounded();
        helper.assertTrue(FlightAbility.apply(abilities, true), "the first call changes the abilities");
        helper.assertTrue(!FlightAbility.apply(abilities, true),
                "a second call in the same state must report no change, because the caller only sends "
                        + "ClientboundPlayerAbilitiesPacket when this returns true");

        helper.assertTrue(FlightAbility.apply(abilities, false),
                "revoking from an enabled state IS a change, so it does ask for a packet");
        helper.assertTrue(!FlightAbility.apply(abilities, false),
                "and revoking again reports none");
        helper.assertTrue(!abilities.mayfly && !abilities.flying, "ending where it began");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aHalfArmedStateIsRepaired(GameTestHelper helper) {
        Abilities abilities = grounded();
        abilities.mayfly = true;
        helper.assertTrue(!abilities.flying, "the state this bug actually left behind");

        helper.assertTrue(FlightAbility.apply(abilities, true),
                "that half-armed state counts as a change, so the tick loop repairs it instead of "
                        + "deciding there is nothing to do");
        helper.assertTrue(abilities.flying, "and flying ends up set");
        helper.succeed();
    }
}
