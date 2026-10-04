package ru.adaptionwheel.server;

import net.minecraft.world.entity.player.Abilities;

/**
 * The single definition of what the Flight mutation does to the abilities object: it grants
 * <em>permission</em> to fly, once, and then stops touching the flag that actually flies.
 *
 * <p>The bug this exists to prevent is subtle, because both halves look correct on their own. The
 * tick loop used to write {@code mayfly = true, flying = true} every tick, and that does lift the
 * player off the ground — but it also means the player can never come back down. Four vanilla
 * sites, each individually reasonable, add up to the trap:
 *
 * <ul>
 *   <li>{@code LocalPlayer.aiStep} — double-tapping jump toggles {@code abilities.flying}
 *       ({@code abilities.flying = !abilities.flying}); the whole branch is guarded by
 *       {@code abilities.mayfly}, so permission is all the toggle needs.</li>
 *   <li>{@code LocalPlayer.aiStep} again — touching the ground clears it:
 *       {@code if (onGround && abilities.flying && !gameMode.isAlwaysFlying()) abilities.flying =
 *       false;}. Note {@code isAlwaysFlying()} means <em>spectator</em>
 *       ({@code MultiPlayerGameMode.isAlwaysFlying()} returns {@code localPlayerMode ==
 *       GameType.SPECTATOR}), not "the flying flag".</li>
 *   <li>{@code ServerGamePacketListenerImpl} — the server adopts whatever the client sent:
 *       {@code player.getAbilities().flying = packet.isFlying() && player.getAbilities().mayfly}.</li>
 *   <li>This class, called from the wearer tick — which put it straight back to {@code true} on the
 *       next tick, authoritatively.</li>
 * </ul>
 *
 * <p>So the player's toggle and their landing both lasted a single tick, and "from flight mode you
 * cannot get out" is exactly what that looks like. The fix is not to withhold {@code flying} on
 * grant — the player should not have to find a key to take off — it is to write it <em>once</em> on
 * the transition and then leave it alone.
 *
 * <p>Returns whether anything changed, so the caller only sends {@code ClientboundPlayerAbilitiesPacket}
 * on a real transition instead of every tick.
 */
public final class FlightAbility {

    private FlightAbility() {
    }

    /**
     * @param permitted whether the player may fly at all
     * @return whether the abilities changed, i.e. whether the caller owes a packet
     */
    public static boolean apply(Abilities abilities, boolean permitted) {
        if (abilities.mayfly != permitted) {
            // A transition. Lift off immediately so the mutation feels like flight rather than like
            // finding a key, and shut down cleanly when it is taken away.
            abilities.mayfly = permitted;
            abilities.flying = permitted;
            return true;
        }
        // Permission already matches, so `flying` belongs to the player from here: vanilla's double-tap
        // and its landing both write to it, and so does the server. Writing it again on the next tick
        // would overwrite both, which is the whole bug. The one state worth repairing is flying with no
        // permission — a half-armed save — which vanilla never produces on its own.
        if (!permitted && abilities.flying) {
            abilities.flying = false;
            return true;
        }
        return false;
    }
}
