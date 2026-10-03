package ru.adaptionwheel.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import ru.adaptionwheel.data.ModDataComponents;
import ru.adaptionwheel.sound.ModSounds;

import java.util.List;

public class SwordOfExterminationItem extends SwordItem {

    public SwordOfExterminationItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    public static boolean isCursed(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.SWORD_MODE.get(), false);
    }

    private static void setMode(ItemStack stack, boolean cursed) {
        stack.set(ModDataComponents.SWORD_MODE.get(), cursed);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            boolean cursed = !isCursed(stack);
            setMode(stack, cursed);
            level.playSound(null, player.blockPosition(), ModSounds.SWING.get(), SoundSource.PLAYERS, 1f, 1.2f);
            player.displayClientMessage(Component.translatable(cursed
                            ? "adaptionwheel.sword.mode_cursed" : "adaptionwheel.sword.mode_positive")
                    .withStyle(cursed ? ChatFormatting.AQUA : ChatFormatting.WHITE), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("adaptionwheel.sword.switch_hint").withStyle(ChatFormatting.GRAY));
        if (isCursed(stack)) {
            tooltip.add(Component.translatable("adaptionwheel.sword.cursed_info").withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("adaptionwheel.sword.positive_info").withStyle(ChatFormatting.WHITE));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.hurtEnemy(stack, target, attacker);
        Level level = attacker.level();
        if (!level.isClientSide) {
            level.playSound(null, target.blockPosition(),
                    isCursed(stack) ? ModSounds.SOE_HIT_2.get() : ModSounds.SOE_HIT_1.get(),
                    SoundSource.PLAYERS, 0.9f, 1f);
        }
        return result;
    }
}
