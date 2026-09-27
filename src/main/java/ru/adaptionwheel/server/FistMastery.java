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
 * Fist Mastery — the adaptation to breaking, and the five material tiers that grow out of it.
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

    /**
     * The material tier the fist currently reaches. Delegates to
     * {@link FistTiers#reachTier} so the client mirror cannot disagree — see there for why that
     * matters.
     *
     * @return {@code 0..4} once the fist exists, {@code -1} when it does not.
     */
    public static int currentTier(PlayerAdaption data) {
        if (!data.isAdapted(Concepts.MUTATION_FIST)) {
            return -1;
        }
        return FistTiers.reachTier(tier -> data.level(FistTiers.concept(tier)));
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
     *
     * <p>Must answer on the client too. The check feeds the {@code /30} divisor inside
     * {@code getDestroyProgress}, and the client's own progress accumulation is what decides how
     * long the player spends holding the button — staying server-only made every block take
     * 3.3x longer than intended, which reads as "the stone fist mines as slow as a bare hand".</p>
     */
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

    /**
     * The fist's bare-hand mining speed multiplier, or {@code 1} when the fist is not in play (no
     * wheel, locked, or a tool/weapon in hand — a held tool keeps its own speed).
     *
     * <p>Applied as a <em>multiplier</em>, not an absolute overwrite. Vanilla computes a bare
     * hand's speed as {@code 1.0 × BLOCK_BREAK_SPEED × haste × efficiency ÷ 5 when airborne ×
     * submerged penalty}, and all of that is already in {@code newSpeed} by the time this event
     * fires. Multiplying swaps the 1.0 for the tool's speed and keeps every modifier intact;
     * assigning an absolute value instead would silently hand the fist night vision in mid-air
     * and ignore haste, because the modifiers upstream were thrown away.</p>
     */
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
        // Measured against the block actually in front of the player, so a tool with per-block
        // speeds is matched exactly rather than approximated by one number.
        return newSpeed * FistTiers.vanillaMiningSpeed(tier, target)
                * (float) (double) AdaptionConfig.FIST_SPEED_SCALE.get();
    }

    // ================= PROGRESSION =================

    /**
     * Handles one bare-handed break: unlocks the fist, or feeds and levels the current tier.
     * Invoked from {@link DomainTriggers} once the block has actually been destroyed.
     */
    public static void onHandBreak(ServerPlayer player, PlayerAdaption data, BlockState state) {
        // Adversity freezes every other analysis, and the fist is one. Without this the fist
        // kept training through a challenge that is supposed to halt all progression.
        if (data.adversityActive) {
            return;
        }
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
        //
        // Membership in the *current* tier's tag, not "the lowest tier that claims this block".
        // Those differ wherever a material is deliberately shared, which is what makes a tier
        // with too little to mine trainable at all: the whole deepslate family is stone-band, so
        // tierOf() calls all of it stone and a diamond fist could never be raised on any of it.
        if (!state.is(FistTiers.tag(tier))) {
            return;
        }
        int need = AdaptionConfig.fistBlocksForNextLevel(tier, level);
        int have = TIER_PROGRESS.merge(player.getUUID(), 1, Integer::sum);
        if (have < need) {
            pushProgress(player, have, need);
            return;
        }
        TIER_PROGRESS.put(player.getUUID(), 0);
        AdaptionEvents.grantConceptLevel(player, data, FistTiers.concept(tier));

        if (level + 1 >= PlayerAdaption.MAX_LEVEL) {
            announceTierUp(player, tier);
        } else {
            // The level just went up, so the next requirement is a different number.
            int newLevel = level + 1;
            pushProgress(player, 0, AdaptionConfig.fistBlocksForNextLevel(tier, newLevel));
        }
    }

    /**
     * Pushes the counter to the one player it concerns, immediately. The regular adaptation sync
     * only goes out once a second, so without this a break sat invisible in the HUD bar for up
     * to a full second — the "I break a block and the bar only moves 100-700 ms later" report.
     * Four bytes, and only on an actual counted break.
     */
    private static void pushProgress(ServerPlayer player, int done, int total) {
        ru.adaptionwheel.network.FistProgressPayload.send(player, done, total);
    }

    private static void announceTierUp(ServerPlayer player, int tier) {
        if (tier + 1 < FistTiers.TIER_COUNT) {
            int next = tier + 1;
            player.sendSystemMessage(Component.translatable("adaptionwheel.msg.fist_tier_up",
                            Concepts.chatName(FistTiers.concept(next)))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(FistTiers.color(next))).withBold(true)));
            // Say what to actually mine, otherwise the player is left guessing why the
            // announced material still gives nothing.
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
        // Point at the first thing worth doing: stone does not drop yet, the Wood tier does not
        // need it, and without this the player has no idea what trains the fist from here.
        player.sendSystemMessage(Component.translatable("adaptionwheel.msg.fist_material_hint",
                        Component.translatable("adaptionwheel.fist.material." + FistTiers.concept(0)),
                        AdaptionConfig.fistBlocksForNextLevel(0, 0))
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(FistTiers.color(0)))));
    }
}
