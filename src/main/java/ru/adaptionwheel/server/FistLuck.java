package ru.adaptionwheel.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.category.FistTiers;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.data.PlayerAdaption;

import java.util.ArrayList;
import java.util.List;

/**
 * The fist's "luck": a tier-scaled multiplier on what a broken ore drops and what it pays in
 * experience, topping out at x10.
 *
 * <p>Hooked on {@link BlockDropsEvent} rather than by rolling the loot table again. That event
 * sits after the loot table has produced its list but before anything enters the world, and it
 * carries both the items and the experience in one object — so drops and XP stay in lockstep, and
 * fortune, silk touch and every loot modifier already baked their own results in before this
 * multiplies them.</p>
 *
 * <p>Reached through {@code Block.playerDestroy -> Block.dropResources -> CommonHooks
 * .handleBlockDrops}, i.e. the ordinary path for a player breaking a block. It is gated on
 * {@code PlayerEvent.HarvestCheck} upstream, so a block the fist cannot harvest never gets here
 * at all.</p>
 *
 * <p>Applies during Adversity, unlike levelling. Luck is a property of how strong the fist
 * currently is, in the same family as the harvest gate and the speed, and all three of those stay
 * live through a challenge; only progress freezes.</p>
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID)
public final class FistLuck {

    private FistLuck() {
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!AdaptionConfig.FIST_LUCK_ENABLED.get()) {
            return;
        }
        if (!(event.getBreaker() instanceof ServerPlayer player)) {
            return;
        }
        BlockState state = event.getState();
        if (!state.is(FistTiers.luckTag())) {
            return;
        }
        PlayerAdaption data = AdaptionEvents.dataOf(player);
        int multiplier = FistTiers.luckMultiplier(FistMastery.currentTier(data));
        if (multiplier <= 1 || event.getDrops().isEmpty()) {
            return;
        }
        multiplyDrops(event, multiplier);
        // Matched to the drops on purpose: x10 ore and x1 ore XP would read as a bug.
        event.setDroppedExperience(event.getDroppedExperience() * multiplier);
    }

    /**
     * Multiplies the already-rolled drop list, merging each stack up to its limit before adding
     * more entities. Ten separate entities for one piece of raw iron would work, but a single
     * stack of ten is what the player expects from "x10" and it is far cheaper to handle.
     */
    private static void multiplyDrops(BlockDropsEvent event, int multiplier) {
        List<ItemEntity> drops = event.getDrops();
        List<ItemEntity> originals = new ArrayList<>(drops);
        List<ItemEntity> result = new ArrayList<>(originals.size());
        for (ItemEntity drop : originals) {
            ItemStack stack = drop.getItem();
            if (stack.isEmpty()) {
                result.add(drop);
                continue;
            }
            int cap = Math.max(1, stack.getMaxStackSize());
            long total = (long) stack.getCount() * multiplier;
            boolean first = true;
            while (total > 0) {
                int count = (int) Math.min(cap, total);
                total -= count;
                ItemStack part = stack.copyWithCount(count);
                if (first) {
                    // Reuse the entity vanilla already built, so the loot's own pickup delay and
                    // any other mod's positioning survives on the first stack.
                    drop.setItem(part);
                    result.add(drop);
                    first = false;
                } else {
                    result.add(copyNear(drop, part));
                }
            }
        }
        drops.clear();
        drops.addAll(result);
    }

    private static ItemEntity copyNear(ItemEntity original, ItemStack stack) {
        ItemEntity copy = new ItemEntity(original.level(), original.getX(), original.getY(),
                original.getZ(), stack);
        copy.setDeltaMovement(original.getDeltaMovement());
        copy.setDefaultPickUpDelay();
        return copy;
    }
}
