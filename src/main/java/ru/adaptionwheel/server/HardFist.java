package ru.adaptionwheel.server;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import ru.adaptionwheel.category.CombatFistTiers;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.category.FistTiers;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.item.SwordOfExterminationItem;


/**
 * The punching fist: five material stages, walked by killing what you are punching.
 *
 * <p>Unlock and every level are earned the same way — kill a hostile mob with a bare hand. Not a hit: a
 * kill, with the last blow landed by the hand and nothing in it that adds attack damage. That is the
 * "something no normal player does" gate, and it is also why the ladder cannot be side-stepped by
 * swinging at a cow.
 */
public final class HardFist {


    private HardFist() {
    }

    /**
     * Kept so the unequip and logout paths stay symmetrical with {@link FistMastery#forget}.
     *
     * <p>The kill counter itself is not dropped: it lives in {@code PlayerAdaption#progress}, which
     * persists. It used to be a static map this method cleared, so the punching fist's "3/7 kills"
     * read as "0/7" on every rejoin.
     */
    public static void forget(java.util.UUID id) {
    }

    public static boolean enabled() {
        return AdaptionConfig.FIST_DAMAGE_ENABLED.get() && AdaptionConfig.ENABLE_COMBAT.get();
    }

    /** The stage the player is training, or -1 before the first bare-handed kill. */
    public static int currentTier(PlayerAdaption data) {
        int granted = -1;
        for (int i = 0; i < CombatFistTiers.TIER_COUNT; i++) {
            if (data.level(CombatFistTiers.concept(i)) > 0) {
                granted = i;
            }
        }
        return granted;
    }

    /**
     * The stage the player is training <i>right now</i>, which is not always the one they reached.
     *
     * <p>A stage tops out at 8 and the next begins at 1, so once the current one is maxed the kills
     * have to go somewhere: they train the next stage, which is the whole point of the 8 -&gt; 1
     * handover. Separate from {@link #currentTier} on purpose -- that answers "which stage has this
     * player ever reached", which is permanent and must stay that way so the HUD row survives the
     * off switch.
     *
     * <p>Both the banking and the row that reports it have to agree on this value. They did not: the
     * row showed the next stage's cost ("0/6 kills") while {@code onFistKill} early-returned on the
     * maxed stage and banked nothing, so a player could kill a hundred mobs against a bar that never
     * moved. One accessor, used by both, is what stops that coming back.
     *
     * @return the stage being trained, or -1 before the first unlock
     */
    public static int trainingTier(PlayerAdaption data) {
        int reached = currentTier(data);
        if (reached < 0) {
            return -1;
        }
        if (data.levelOrZero(CombatFistTiers.concept(reached)) >= PlayerAdaption.MAX_LEVEL
                && reached + 1 < CombatFistTiers.TIER_COUNT) {
            return reached + 1;
        }
        return reached;
    }

    /**
     * Whether this kill trains the punching fist.
     *
     * <p>Decided from the **damage source**, not from what is in the hand at the moment of death.
     * Asking the hand was wrong in both directions: a bow carries no {@code ATTACK_DAMAGE} modifier,
     * so a bow kill counted as bare-handed, and the item in hand at death is not necessarily the
     * item that swung.
     *
     * <p>Ranged never counts -- a bow, a crossbow, the Extermination Sword's cursed slash and the
     * Dimension Destroy rifts all arrive with a projectile as the direct entity. The Extermination
     * Sword is the one exception and it has to be named, because it is literally a piece of
     * Mahoraga: it counts when swung in **positive-energy mode** only, and its own cursed mode is
     * excluded twice over, since that mode's damage comes from the projectile the melee check has
     * already rejected.
     */
    public static boolean trains(ServerPlayer player, DamageSource source) {
        if (!enabled()) {
            return false;
        }
        if (source.getDirectEntity() != null && !(source.getDirectEntity() instanceof Player)) {
            return false;
        }
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof SwordOfExterminationItem sword
                && !SwordOfExterminationItem.isCursed(held)) {
            return true;
        }
        // Anything else that adds attack damage in the hand is a weapon: axe, sword, trident.
        // A fish has none and counts, which is the intended behaviour.
        return !FistTiers.dealsExtraAttackDamage(held);
    }

    /**
     * Damage added to a hit, before {@code applyOffense} scales it, so a weapon's own damage, the
     * per-mob offense bonus and the crit roll all still apply on top.
     *
     * <p>Every trained stage contributes, rather than only the highest. A stage's own
     * (base + perLevel x level) times its material multiplier is smaller at level 1 than a lower
     * material is at max — wooden Lv.8 is 7.0 but stone Lv.1 is 3.5 — so keying off the highest
     * stage would pay out LESS the moment a player advanced, which is the wrong direction for a
     * reward. Summing is monotonic in both level and stage: training can never lose damage, and the
     * later materials still dominate because they carry the larger multipliers.
     */
    public static float bonus(ServerPlayer player, PlayerAdaption data) {
        if (!AdaptionConfig.FIST_DAMAGE_ENABLED.get()) {
            return 0f;
        }
        float perAdaptation = 1f + data.getAdaptCount()
                * AdaptionConfig.FIST_DAMAGE_PER_ADAPTATION.get().floatValue();
        float total = 0f;
        for (int tier = 0; tier < CombatFistTiers.TIER_COUNT; tier++) {
            int level = data.levelOrZero(CombatFistTiers.concept(tier));
            if (level <= 0) {
                continue;
            }
            float stageBase = (float) (AdaptionConfig.FIST_DAMAGE_BASE.get()
                    + AdaptionConfig.FIST_DAMAGE_PER_LEVEL.get() * level);
            total += stageBase * AdaptionConfig.fistDamageTierMultiplier(tier);
        }
        return total * perAdaptation;
    }

    public static void onFistKill(ServerPlayer player, PlayerAdaption data, LivingEntity dead,
                                  DamageSource source) {
        if (data.adversityActive || !trains(player, source)) {
            return;
        }
        int tier = trainingTier(data);
        if (tier < 0) {
            unlock(player, data);
            return;
        }
        String concept = CombatFistTiers.concept(tier);
        // levelOrZero, not level(): banking kills is an effect, so a switched-off stage must stop
        // counting. The level read here decides which stage the kills train, and it is the same
        // accessor disagreement that let a disabled adaptation keep paying out.
        int level = data.levelOrZero(concept);
        if (level >= PlayerAdaption.MAX_LEVEL) {
            // Only reachable on a finished ladder: there is no stage after Netherite to train.
            return;
        }
        int need = AdaptionConfig.fistKillsForNextLevel(tier, level);
        // Persisted, not a static map: these are kills the player earned, and a static map is
        // cleared on logout, so the row reset to 0 on every rejoin.
        String key = ru.adaptionwheel.data.Extras.punchingKey(tier);
        int have = data.progress.getOrDefault(key, 0) + 1;
        if (have < need) {
            data.progress.put(key, have);
            pushProgress(player, have, need, tier);
            return;
        }
        data.progress.remove(key);
        AdaptionEvents.completeTask(player, data, concept);
        if (level + 1 >= PlayerAdaption.MAX_LEVEL) {
            announceStageUp(player, tier);
            // Maxing a stage hands over to the next one, so the row has to say which is now being
            // trained; leaving the finished stage's numbers up would freeze the bar at its last fill.
            int next = tier + 1;
            if (next < CombatFistTiers.TIER_COUNT) {
                pushProgress(player, 0, AdaptionConfig.fistKillsForNextLevel(next, 0), next);
            }
        } else {
            pushProgress(player, 0, AdaptionConfig.fistKillsForNextLevel(tier, level + 1), tier);
        }
    }

    private static void unlock(ServerPlayer player, PlayerAdaption data) {
        AdaptionEvents.completeTask(player, data, CombatFistTiers.concept(0));
        player.sendSystemMessage(Component.translatable("adaptionwheel.msg.fist_damage_unlocked")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(CombatFistTiers.color(0))).withBold(true)));
        player.sendSystemMessage(Component.translatable("adaptionwheel.msg.fist_damage_hint",
                        Component.translatable("adaptionwheel.fist.material." + CombatFistTiers.concept(0)),
                        AdaptionConfig.fistKillsForNextLevel(0, 1))
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(CombatFistTiers.color(0)))));
    }

    private static void announceStageUp(ServerPlayer player, int tier) {
        if (tier + 1 >= CombatFistTiers.TIER_COUNT) {
            return;
        }
        int next = tier + 1;
        player.sendSystemMessage(Component.translatable("adaptionwheel.msg.fist_damage_tier_up",
                        Concepts.chatName(CombatFistTiers.concept(next)))
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(CombatFistTiers.color(next))).withBold(true)));
        player.sendSystemMessage(Component.translatable("adaptionwheel.msg.fist_damage_hint",
                        Component.translatable("adaptionwheel.fist.material." + CombatFistTiers.concept(next)),
                        AdaptionConfig.fistKillsForNextLevel(next, 0))
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(CombatFistTiers.color(next)))));
    }

    private static void pushProgress(ServerPlayer player, int done, int total, int tier) {
        ru.adaptionwheel.network.CombatFistProgressPayload.send(player, done, total, tier);
    }
}
