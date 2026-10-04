package ru.adaptionwheel.server;

import net.minecraft.world.entity.player.Abilities;

/**
 * The single definition of what "Flight makes the player fly" means on the abilities object.
 *
 * <p>Both flags are required, and setting only {@code mayfly} is the trap: {@code mayfly} merely ARMS
 * vanilla's double-tap-to-fly, and the client cancels it again the moment the player is standing on
 * something ({@code LocalPlayer}: {@code if (this.onGround() && abilities.flying && !isAlwaysFlying())
 * abilities.flying = false;}). A mutation that grants {@code mayfly} and leaves {@code flying} false
 * therefore looks correct server-side and never leaves the ground. Vanilla's own creative mode sets
 * both.
 *
 * <p>Returns whether anything changed, so the caller only sends the abilities packet on a real
 * transition instead of every tick.
 */
public final class FlightAbility {

    private FlightAbility() {
    }

    public static boolean apply(Abilities abilities, boolean enabled) {
        boolean wantFlying = enabled;
        if (abilities.mayfly == wantFlying && abilities.flying == wantFlying) {
            return false;
        }
        abilities.mayfly = wantFlying;
        abilities.flying = wantFlying;
        return true;
    }
}
