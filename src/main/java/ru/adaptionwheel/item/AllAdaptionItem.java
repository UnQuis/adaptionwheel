package ru.adaptionwheel.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import ru.adaptionwheel.server.AdaptionEvents;

/** Instantly grants all adaptations when eaten. Requires the Mahoraga Wheel to be equipped. */
public class AllAdaptionItem extends Item {

    public AllAdaptionItem(Properties properties) {
        super(properties.food(new FoodProperties.Builder()
                .nutrition(0)
                .saturationModifier(0f)
                .alwaysEdible()
                .build()));
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.EAT;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 20;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (entity instanceof Player player && !level.isClientSide()) {
            AdaptionEvents.grantAllAdaptations(player);
        }
        return result;
    }
}
