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

@EventBusSubscriber(modid = AdaptionWheel.MODID)
public final class FistMastery {

    private static final Map<UUID, Boolean> INSTABREAK_STANCE = new HashMap<>();


    private FistMastery() {
    }

    public static int currentTier(PlayerAdaption data) {
        if (!data.active(Concepts.MUTATION_FIST)) {
            return -1;
        }
        return FistTiers.reachTier(tier -> data.level(FistTiers.concept(tier)));
    }

    public static boolean isMaxed(PlayerAdaption data, int tier) {
        return data.level(FistTiers.concept(tier)) >= PlayerAdaption.MAX_LEVEL;
    }

    public static boolean enabled() {
        return AdaptionConfig.FIST_ENABLED.get() && AdaptionConfig.ENABLE_MINING.get();
    }

    public static boolean instabreakUnlocked(PlayerAdaption data) {
        return AdaptionConfig.FIST_INSTABREAK_ENABLED.get()
                && isMaxed(data, FistTiers.TIER_COUNT - 1);
    }

    public static boolean instabreakStance(ServerPlayer player) {
        return Boolean.TRUE.equals(INSTABREAK_STANCE.get(player.getUUID()));
    }

    /**
     * Blocks earned toward the tier's next level.
     *
     * <p>Lives in {@link PlayerAdaption#progress} rather than in the static map, because it is
     * something the player earned: a static map is cleared by {@link #forget} on logout, so the
     * counter read "0/166" again on every rejoin. It used to do exactly that. {@code TIER_PROGRESS}
     * is kept only as a per-tick working value and mirrors the attachment, so a count is never lost
     * and never double-counted — the attachment is the single source of truth across a session.
     */
    public static int tierProgress(UUID id, PlayerAdaption data) {
        int tier = currentTier(data);
        return data.progress.getOrDefault(ru.adaptionwheel.data.Extras.miningKey(tier), 0);
    }

    public static void forget(UUID id) {
        INSTABREAK_STANCE.remove(id);
    }

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

    @SubscribeEvent
    public static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        if (event.canHarvest() || !enabled() || !AdaptionConfig.FIST_HARVEST_WITHOUT_TOOL.get()) {
            return;
        }
        Player player = event.getEntity();
        if (!SurfaceAdaptations.wearingWheel(player)
                || !FistTiers.usableWith(player.getMainHandItem(), event.getTargetBlock())) {
            return;
        }
        int tier = SurfaceAdaptations.fistTier(player);
        if (tier >= 0 && FistTiers.canHarvest(event.getTargetBlock(), tier)) {
            event.setCanHarvest(true);
        }
    }

    public static float breakSpeed(Player player, float newSpeed, BlockState target) {
        if (!enabled() || newSpeed <= 0f || !FistTiers.usableWith(player.getMainHandItem(), target)) {
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

        return newSpeed * FistTiers.vanillaMiningSpeed(tier, target)
                * (float) (double) AdaptionConfig.FIST_SPEED_SCALE.get();
    }

    public static void onHandBreak(ServerPlayer player, PlayerAdaption data, BlockState state) {

        if (data.adversityActive) {
            return;
        }
        int tier = currentTier(data);
        if (tier < 0) {
            tryUnlock(player, data, state);
            return;
        }
        int level = data.levelOrZero(FistTiers.concept(tier));
        if (level >= PlayerAdaption.MAX_LEVEL) {
            return;
        }

        if (!state.is(FistTiers.tag(tier))) {
            return;
        }
        int need = AdaptionConfig.fistBlocksForNextLevel(tier, level);
        String key = ru.adaptionwheel.data.Extras.miningKey(tier);
        int have = data.progress.getOrDefault(key, 0) + 1;
        if (have < need) {
            data.progress.put(key, have);
            pushProgress(player, have, need);
            return;
        }
        data.progress.remove(key);
        AdaptionEvents.grantConceptLevel(player, data, FistTiers.concept(tier));

        if (level + 1 >= PlayerAdaption.MAX_LEVEL) {
            announceTierUp(player, tier);
        } else {

            int newLevel = level + 1;
            pushProgress(player, 0, AdaptionConfig.fistBlocksForNextLevel(tier, newLevel));
        }
    }

    private static void pushProgress(ServerPlayer player, int done, int total) {
        ru.adaptionwheel.network.FistProgressPayload.send(player, done, total);
    }

    private static void announceTierUp(ServerPlayer player, int tier) {
        if (tier + 1 < FistTiers.TIER_COUNT) {
            int next = tier + 1;
            player.sendSystemMessage(Component.translatable("adaptionwheel.msg.fist_tier_up",
                            Concepts.chatName(FistTiers.concept(next)))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(FistTiers.color(next))).withBold(true)));

            player.sendSystemMessage(Component.translatable("adaptionwheel.msg.fist_material_hint",
                            Component.translatable("adaptionwheel.fist.material." + FistTiers.concept(next)),
                            AdaptionConfig.fistBlocksForNextLevel(next, 0))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(FistTiers.color(next)))));
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

    private static void tryUnlock(ServerPlayer player, PlayerAdaption data, BlockState state) {
        if (data.levelOrZero(Concepts.MINE_LABOR) < PlayerAdaption.MAX_LEVEL) {
            return;
        }
        if (FistTiers.tierOf(state) != 1) {
            return;
        }
        AdaptionEvents.grantComboMutation(player, data, Concepts.MUTATION_FIST);

        player.sendSystemMessage(Component.translatable("adaptionwheel.msg.fist_material_hint",
                        Component.translatable("adaptionwheel.fist.material." + FistTiers.concept(0)),
                        AdaptionConfig.fistBlocksForNextLevel(0, 0))
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(FistTiers.color(0)))));
    }
}
