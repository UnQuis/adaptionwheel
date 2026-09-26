package ru.adaptionwheel.server;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.SurfaceAdaptations;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.category.FistTiers;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.data.PlayerAdaption;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fist Mastery — the adaptation to breaking, and the six material tiers that grow out of it.
 *
 * <p>Unlocking: max {@link Concepts#MINE_LABOR} and break a block of the Stone class
 * bare-handed. From then on the wearer's bare hand counts as a tool for every block up to the
 * current tier's material, so tools become optional rather than mandatory.</p>
 *
 * <p>Progression: a level is earned only by breaking blocks belonging to the <em>current</em>
 * tier's own material, and the next tier opens only once the current one is maxed. Those blocks
 * are both rarer and slower to break by hand than the previous tier's, and the per-tier cost
 * multiplier stacks on top of that.</p>
 *
 * <p>Instabreak: the last level of the last tier (Netherite 8) removes any breakable block in a
 * single tick. It is deliberately opt-in through a keybind, so the player can switch it off
 * whenever they don't want to accidentally delete a build.</p>
 *
 * <p>Client-side mirrors of {@link #currentTier}, {@link #instabreakUnlocked} and the stance
 * live in {@link SurfaceAdaptations}, which dispatches on the asking side.</p>
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID)
public final class FistMastery {

    /** Runtime-only: Instabreak is a stance, not adaptation data, so it never reaches the wheel. */
    private static final Map<UUID, Boolean> INSTABREAK_STANCE = new HashMap<>();

    /** Runtime-only: blocks broken toward the current tier's next level. */
    private static final Map<UUID, Integer> TIER_PROGRESS = new HashMap<>();

    private FistMastery() {
    }

    // ================= STATE =================

    /** Highest tier index the wearer has reached; {@code 0} once the fist exists, {@code -1} if not. */
    public static int currentTier(PlayerAdaption data) {
        if (!data.isAdapted(Concepts.MUTATION_FIST)) {
            return -1;
        }
        for (int i = FistTiers.TIER_COUNT - 1; i >= 0; i--) {
            if (data.level(FistTiers.concept(i)) > 0) {
                return i;
            }
        }
        return 0;
    }

    public static boolean isMaxed(PlayerAdaption data, int tier) {
        return data.level(FistTiers.concept(tier)) >= PlayerAdaption.MAX_LEVEL;
    }

    /** Fist Mastery lives in the mining module, so it follows that module's master switch. */
    public static boolean enabled() {
        return AdaptionConfig.FIST_ENABLED.get() && AdaptionConfig.ENABLE_MINING.get();
    }

    /** Instabreak needs the very last level of the very last tier. */
    public static boolean instabreakUnlocked(PlayerAdaption data) {
        return AdaptionConfig.FIST_INSTABREAK_ENABLED.get()
                && isMaxed(data, FistTiers.TIER_COUNT - 1);
    }

    public static boolean instabreakStance(ServerPlayer player) {
        return Boolean.TRUE.equals(INSTABREAK_STANCE.get(player.getUUID()));
    }

    /** Blocks of the current tier's own material counted toward the next level (not yet clamped). */
    public static int tierProgress(UUID id) {
        return TIER_PROGRESS.getOrDefault(id, 0);
    }

    public static void forget(UUID id) {
        INSTABREAK_STANCE.remove(id);
        TIER_PROGRESS.remove(id);
    }

    // ================= INSTABREAK STANCE =================

    /**
     * Server-authoritative stance switch. Rejects the request when Instabreak is not actually
     * unlocked and resyncs the real value straight away, so a tampered client cannot hold the
     * stance on.
     */
    public static void setInstabreak(ServerPlayer player, boolean active) {
        PlayerAdaption data = AdaptionEvents.dataOf(player);
        if (!AdaptionEvents.isWearingWheel(player) || !instabreakUnlocked(data)) {
            INSTABREAK_STANCE.put(player.getUUID(), false);
            AdaptionEvents.syncAdaption(player, data, true);
            return;
        }
        if (instabreakStance(player) == active) {
            return;
        }
        INSTABREAK_STANCE.put(player.getUUID(), active);
        AdaptionEvents.syncAdaption(player, data, true);
    }

    // ================= EVENTS =================

    /**
     * Lets the fist stand in for a tool. This single override is what makes the fist collect
     * anything: vanilla gates both the drops and the destroy-speed divisor behind the same
     * harvest check, so answering true here grants the drops <em>and</em> the "correct tool"
     * speed bonus (divide by 30 rather than 100) in a single place. No mixin required.
     */
    @SubscribeEvent
    public static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        if (event.canHarvest() || !enabled() || !AdaptionConfig.FIST_HARVEST_WITHOUT_TOOL.get()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide) {
            return;
        }
        if (!AdaptionEvents.isWearingWheel(player) || !player.getMainHandItem().isEmpty()) {
            return;
        }
        int tier = currentTier(AdaptionEvents.dataOf(player));
        if (tier >= 0 && FistTiers.canHarvest(event.getTargetBlock(), tier)) {
            event.setCanHarvest(true);
        }
    }

    /**
     * Bare-hand mining speed from the fist, plus the Instabreak override. Folded into the
     * existing Mine_Labor {@code BreakSpeed} handler so the two mining adaptations compose
     * instead of overwriting each other.
     */
    public static float breakSpeedBonus(Player player, float newSpeed) {
        if (!enabled() || !player.getMainHandItem().isEmpty()) {
            return newSpeed;
        }
        int tier = SurfaceAdaptations.fistTier(player);
        if (tier < 0) {
            return newSpeed;
        }
        if (AdaptionConfig.FIST_INSTABREAK_ENABLED.get()
                && SurfaceAdaptations.instabreakUnlocked(player)
                && SurfaceAdaptations.instabreakActive(player)) {
            return (float) (double) AdaptionConfig.FIST_INSTABREAK_SPEED.get();
        }
        int level = SurfaceAdaptations.fistLevel(player, tier);
        double bonus = AdaptionConfig.fistTierSpeed(tier)
                + Math.max(0, level - 1) * AdaptionConfig.FIST_SPEED_PER_LEVEL.get();
        return bonus <= 0 ? newSpeed : newSpeed + (float) bonus;
    }

    // ================= PROGRESSION =================

    /**
     * Handles one bare-handed break: unlocks the fist, or feeds and levels the current tier.
     * Invoked from {@link DomainTriggers} once the block has actually been destroyed.
     */
    public static void onHandBreak(ServerPlayer player, PlayerAdaption data, BlockState state) {
        int tier = currentTier(data);
        if (tier < 0) {
            tryUnlock(player, data, state);
            return;
        }
        int level = data.level(FistTiers.concept(tier));
        if (level >= PlayerAdaption.MAX_LEVEL) {
            return;
        }
        // Only the current tier's own material trains it, otherwise the ladder would be
        // side-stepped by farming the softest blocks forever.
        if (FistTiers.tierOf(state) != tier) {
            return;
        }
        int need = AdaptionConfig.fistBlocksForNextLevel(tier, level);
        int have = TIER_PROGRESS.merge(player.getUUID(), 1, Integer::sum);
        if (have < need) {
            return;
        }
        TIER_PROGRESS.put(player.getUUID(), 0);
        AdaptionEvents.grantConceptLevel(player, data, FistTiers.concept(tier));

        if (level + 1 >= PlayerAdaption.MAX_LEVEL) {
            announceTierUp(player, tier);
        }
    }

    private static void announceTierUp(ServerPlayer player, int tier) {
        if (tier + 1 < FistTiers.TIER_COUNT) {
            player.sendSystemMessage(Component.translatable("adaptionwheel.msg.fist_tier_up",
                            Concepts.chatName(FistTiers.concept(tier + 1)))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(FistTiers.color(tier + 1))).withBold(true)));
            return;
        }
        if (!AdaptionConfig.FIST_INSTABREAK_ENABLED.get()) {
            return;
        }
        if (AdaptionConfig.FIST_INSTABREAK_DEFAULT_ON.get()) {
            INSTABREAK_STANCE.put(player.getUUID(), true);
        }
        player.sendSystemMessage(Component.translatable("adaptionwheel.msg.instabreak_unlocked")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
    }

    /**
     * The unlock ritual: a maxed Labor plus a stone block broken by hand. The break itself is
     * the analysis — vanilla already lets a bare hand mine stone, it is just slow and yields
     * nothing, which is precisely the adversity being decoded.
     */
    private static void tryUnlock(ServerPlayer player, PlayerAdaption data, BlockState state) {
        if (data.level(Concepts.MINE_LABOR) < PlayerAdaption.MAX_LEVEL) {
            return;
        }
        if (FistTiers.tierOf(state) != 1) { // Stone class
            return;
        }
        AdaptionEvents.grantComboMutation(player, data, Concepts.MUTATION_FIST);
    }
}
