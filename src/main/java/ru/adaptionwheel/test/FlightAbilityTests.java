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
    public static void grantingLiftsOffAndRevokingShutsDown(GameTestHelper helper) {
        Abilities abilities = grounded();
        helper.assertTrue(!abilities.mayfly && !abilities.flying, "vanilla starts with neither");

        helper.assertTrue(FlightAbility.apply(abilities, true), "the first grant changes something");
        helper.assertTrue(abilities.mayfly, "permission is granted");
        helper.assertTrue(abilities.flying,
                "and the player is lifted off on the transition, so the mutation feels like flight "
                        + "rather than like hunting for a key");

        helper.assertTrue(FlightAbility.apply(abilities, false), "revoking from that state is a change");
        helper.assertTrue(!abilities.mayfly, "permission is taken back");
        helper.assertTrue(!abilities.flying, "and flight with it, or a revoked mutation would keep you aloft");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void onceGrantedThePlayerOwnsTheFlyingFlag(GameTestHelper helper) {
        // The reported bug, verbatim: "you cannot get out of flight mode".
        //
        // The player's own two ways out both write `flying = false` — double-tapping jump to toggle
        // off, and simply landing, which vanilla clears on ground contact. The server adopts either
        // one, and the tick loop used to overwrite it on the very next tick.
        Abilities abilities = grounded();
        FlightAbility.apply(abilities, true);

        abilities.flying = false;
        helper.assertTrue(!FlightAbility.apply(abilities, true),
                "a player who has toggled flight off must not have it forced back on: the return value "
                        + "is what decides whether the caller re-sends ClientboundPlayerAbilitiesPacket, "
                        + "so reporting a change here is exactly what makes flight inescapable");

        abilities.flying = true;
        FlightAbility.apply(abilities, true);
        abilities.flying = false;
        helper.assertTrue(!FlightAbility.apply(abilities, true),
                "and the same after a landing: vanilla cleared the flag, the server adopted it, and the "
                        + "wearer tick must leave it alone instead of putting it straight back");
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
        // Landing leaves `mayfly` set and `flying` clear, which is a perfectly good state, so it is
        // not "repaired" — that is the player choosing to be on the ground. Only the opposite, which
        // vanilla never produces, is a stuck state worth fixing.
        Abilities abilities = grounded();
        abilities.mayfly = true;
        helper.assertTrue(!abilities.flying, "landed, not broken");
        helper.assertTrue(!FlightAbility.apply(abilities, true), "and deliberately left alone");
        helper.assertTrue(abilities.mayfly, "permission stays granted while landed");

        abilities.mayfly = false;
        abilities.flying = true;
        helper.assertTrue(FlightAbility.apply(abilities, false),
                "flying with no permission is a stuck state and is cleared");
        helper.assertTrue(!abilities.flying, "so a revoked or half-armed player is not left aloft");
        helper.succeed();
    }
}
