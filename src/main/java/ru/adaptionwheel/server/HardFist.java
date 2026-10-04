package ru.adaptionwheel.server;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import ru.adaptionwheel.category.CombatFistTiers;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.category.FistTiers;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.data.PlayerAdaption;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The punching fist: five material stages, walked by killing what you are punching.
 *
 * <p>Unlock and every level are earned the same way — kill a hostile mob with a bare hand. Not a hit: a
 * kill, with the last blow landed by the hand and nothing in it that adds attack damage. That is the
 * "something no normal player does" gate, and it is also why the ladder cannot be side-stepped by
 * swinging at a cow.
 */
public final class HardFist {

    private static final Map<UUID, Integer> KILL_PROGRESS = new HashMap<>();

    private HardFist() {
    }

    public static void forget(UUID id) {
        KILL_PROGRESS.remove(id);
    }

    public static int killProgress(UUID id) {
        return KILL_PROGRESS.getOrDefault(id, 0);
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

    public static boolean isMaxed(PlayerAdaption data, int tier) {
        return data.level(CombatFistTiers.concept(tier)) >= PlayerAdaption.MAX_LEVEL;
    }

    /** Nothing in the hand that adds attack damage: a fist, not a sword. */
    public static boolean trains(ServerPlayer player) {
        return enabled() && !FistTiers.dealsExtraAttackDamage(player.getMainHandItem());
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
    public static float bonus(PlayerAdaption data) {
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

    public static void onFistKill(ServerPlayer player, PlayerAdaption data, LivingEntity dead) {
        if (data.adversityActive || !trains(player)) {
            return;
        }
        int tier = currentTier(data);
        if (tier < 0) {
            unlock(player, data);
            return;
        }
        String concept = CombatFistTiers.concept(tier);
        int level = data.level(concept);
        if (level >= PlayerAdaption.MAX_LEVEL) {
            return;
        }
        int need = AdaptionConfig.fistKillsForNextLevel(tier, level);
        int have = KILL_PROGRESS.merge(player.getUUID(), 1, Integer::sum);
        if (have < need) {
            pushProgress(player, have, need, tier);
            return;
        }
        KILL_PROGRESS.put(player.getUUID(), 0);
        AdaptionEvents.grantConceptLevel(player, data, concept);
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
        AdaptionEvents.grantConceptLevel(player, data, CombatFistTiers.concept(0));
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
