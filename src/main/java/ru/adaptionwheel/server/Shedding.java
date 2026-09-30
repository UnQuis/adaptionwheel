package ru.adaptionwheel.server;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.category.WheelTier;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.effect.ModEffects;
import ru.adaptionwheel.effect.WildReleaseEffect;
import ru.adaptionwheel.sound.ModSounds;

/**
 * Shedding: deliberately giving an adaptation up, on purpose, for a burst.
 *
 * <p>The mod's whole shape is "everything adapts to you, eventually, for free". That makes it
 * entirely passive — nothing a player does changes <em>what</em> they can adapt to, only how long
 * they wait. Shedding is the one thing that does, and it is the answer to a specific complaint
 * about the pool being a list rather than a build: you can now take a capability off yourself and
 * feel the wheel push back.</p>
 *
 * <p><b>Nothing is lost, and nothing is a price.</b> The concept becomes re-analysable at once, at
 * a fraction of its original timer, because the wheel remembers what it already worked out. Shedding
 * a fully-analysed family to chase something else is therefore a swap that costs minutes, not
 * progress that cannot be recovered — which is the only version of this feature that fits a mod
 * whose premise is omnipotence. For the same reason {@link #canShed} refuses a shed that would
 * drop the wearer's wheel tier: that would take a whole family of adaptations away again, which is
 * the one thing this mod never does to a player.</p>
 *
 * <p>The payout is a real vanilla {@link MobEffect}, so it appears in the ordinary buff readout
 * and its attribute modifiers are cleaned up by the game rather than by this class.</p>
 */
public final class Shedding {

    private Shedding() {
    }

    /** Why a shed cannot go ahead, so the command can say which one it was. */
    public enum Refusal {
        OK,
        DISABLED,
        NOT_HELD,
        /** An analysis is running for it right now, so there is nothing held to give up yet. */
        STILL_ANALYSING,
        DURING_ADVERSITY,
        /** Would cost the wearer a whole family by dropping their wheel tier. */
        WOULD_DROP_TIER
    }

    /**
     * Whether the wearer may shed this concept right now.
     *
     * <p>Checked before anything is changed, and the reasons are distinct because a single generic
     * "you cannot do that" for four different situations is a message a player cannot act on.</p>
     */
    public static Refusal canShed(ServerPlayer player, PlayerAdaption data, String concept) {
        if (!AdaptionConfig.SHEDDING_ENABLED.get()) {
            return Refusal.DISABLED;
        }
        if (data.adversityActive) {
            return Refusal.DURING_ADVERSITY;
        }
        boolean held = data.isAdapted(concept) || data.level(concept) > 0;
        if (!held) {
            // Distinguish "never adapted" from "mid-analysis": the second is a reason to wait a
            // moment rather than a sign the command was typed wrong.
            return isAnalysing(data, concept) ? Refusal.STILL_ANALYSING : Refusal.NOT_HELD;
        }
        if (AdaptionConfig.SHEDDING_PROTECT_TIER.get() && wouldDropTier(data, concept)) {
            return Refusal.WOULD_DROP_TIER;
        }
        return Refusal.OK;
    }

    /**
     * Whether giving this concept up would cost the wearer a wheel tier.
     *
     * <p>Computed against the count the concept itself contributes. Note that shedding a maxed
     * leveled concept usually does not lower the count at all — {@code getAdaptCount} counts a
     * leveled concept as one whether it is level 1 or level 8 — so this only fires for the
     * concept standing alone at the bottom of its family.</p>
     */
    public static boolean wouldDropTier(PlayerAdaption data, String concept) {
        int before = WheelTier.forCount(data.getAdaptCount());
        int contributed = data.isAdapted(concept) || data.level(concept) > 0 ? 1 : 0;
        int after = WheelTier.forCount(Math.max(0, data.getAdaptCount() - contributed));
        return after < before;
    }

    /**
     * Performs the shed.
     *
     * <p>The removal itself goes through {@code AdaptionEvents.debugUngrant}, not a second
     * implementation of it: that method already invalidates the count, re-applies stats and passive
     * effects, clears the fist's runtime state and saves the wheel item, and a shed that skipped
     * any of those would leave a player who had just given something up still wearing its
     * benefits.</p>
     *
     * @return the refusal that stopped it, or {@link Refusal#OK} on success
     */
    public static Refusal shed(ServerPlayer player, PlayerAdaption data, String concept) {
        Refusal refusal = canShed(player, data, concept);
        if (refusal != Refusal.OK) {
            return refusal;
        }
        int level = data.level(concept);
        int amplifier = releaseAmplifier(concept, level);
        int ticks = releaseTicks(level);

        if (!AdaptionEvents.debugUngrant(player, concept)) {
            return Refusal.NOT_HELD;
        }
        // Only after the removal, because debugUngrant re-applies stats from the reduced count and
        // the tier message below has to be about the tier the player is now on.
        data.recentlyShed.add(concept);
        data.shedCount++;
        if (level > 0) {
            // The concept drops to level 0 rather than being forgotten, so a partially-analysed
            // adaptation is not thrown away wholesale by a shed.
            data.levels.put(concept, 0);
        }

        applyRelease(player, amplifier, ticks);
        player.displayClientMessage(Component.translatable("adaptionwheel.msg.shed",
                        Concepts.chatName(concept))
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), false);
        player.displayClientMessage(Component.translatable("adaptionwheel.msg.shed_release",
                        percent(amplifier), Math.max(1, ticks / 20))
                .withStyle(ChatFormatting.GRAY), false);
        player.level().playSound(null, player.blockPosition(), ModSounds.REF.get(),
                SoundSource.PLAYERS, 0.8f, 0.8f);
        // No sync here on purpose: debugUngrant already sent one, and the extra lines below only
        // touch runtime state (recentlyShed) that the client does not mirror.
        return Refusal.OK;
    }

    /**
     * How strong the Wild Release is.
     *
     * <p>Amplifier, not a bespoke stat: the effect registers one set of modifiers and vanilla
     * scales them by {@code amplifier + 1}, so a ladder of four rungs covers the whole range and
     * there is no second table to keep in step with the effect's own numbers.</p>
     *
     * <p>A one-time adaptation always lands on the second rung. Shedding something that took a
     * whole environment to learn should not feel like shedding a level-one contact, and it has no
     * level to scale by.</p>
     */
    public static int releaseAmplifier(String concept, int level) {
        if (level <= 0) {
            return 1;
        }
        return Math.min(WildReleaseEffect.MAX_AMPLIFIER, (level + 1) / 2);
    }

    /** Wild Release duration for a shed adaptation of this level, in ticks. */
    public static int releaseTicks(int level) {
        return Math.max(1, AdaptionConfig.SHEDDING_RELEASE_BASE_TICKS.get()
                + Math.max(0, level) * AdaptionConfig.SHEDDING_RELEASE_TICKS_PER_LEVEL.get());
    }

    /**
     * The re-analysis multiplier for a concept the player has shed.
     *
     * <p>1.0 for anything not shed. Read inside {@code startOrAccelerate} so every route into an
     * analysis gets it — triggers, commands and the browser's own progress all funnel there.</p>
     */
    public static double reattachFactor(PlayerAdaption data, String concept) {
        if (!AdaptionConfig.SHEDDING_ENABLED.get() || !data.recentlyShed.contains(concept)) {
            return 1.0D;
        }
        return AdaptionConfig.SHEDDING_REATTACH_TIMER_FACTOR.get();
    }

    /**
     * Gives the release, replacing a weaker one rather than stacking.
     *
     * <p>Vanilla already keeps the stronger of two same-effect instances, so passing the longer
     * duration is enough; the explicit remove first is what stops a short release from refreshing a
     * long one into being shorter.</p>
     */
    private static void applyRelease(ServerPlayer player, int amplifier, int ticks) {
        // The DeferredHolder itself is passed rather than .get(): every one of these three methods
        // takes a Holder<MobEffect> in 1.21.1, and the holder is what the registry hands back.
        if (player.getEffect(ModEffects.WILD_RELEASE) != null) {
            player.removeEffect(ModEffects.WILD_RELEASE);
        }
        player.addEffect(new MobEffectInstance(ModEffects.WILD_RELEASE, ticks, amplifier,
                true, true, true));
    }

    private static String percent(int amplifier) {
        return "+" + ((amplifier + 1) * 20) + "%";
    }

    /** Whether an analysis is currently running for this concept. */
    private static boolean isAnalysing(PlayerAdaption data, String concept) {
        for (ru.adaptionwheel.data.AdaptionTask task : data.tasks) {
            if (task.concept.equals(concept)) {
                return true;
            }
        }
        return false;
    }
}
