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
        // currentTier answers "which stage did the player reach", which is permanent, so the
        // switch is applied here instead: luck is an effect and must stop when the fist is off.
        // isEnabled(), not active(): the tier concept is LEVELED and active(Fist_*) is always
        // false (leveled concepts never enter the adapted set, see applyGrant). The 1.21.1
        // branch shipped exactly that dead gate; do not reintroduce it.
        int tier = FistMastery.currentTier(data);
        int multiplier = FistTiers.luckMultiplier(
                tier >= 0 && data.isEnabled(FistTiers.concept(tier)) ? tier : -1);
        if (multiplier <= 1 || event.getDrops().isEmpty()) {
            return;
        }
        multiplyDrops(event, multiplier);

        event.setDroppedExperience(event.getDroppedExperience() * multiplier);
    }

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
