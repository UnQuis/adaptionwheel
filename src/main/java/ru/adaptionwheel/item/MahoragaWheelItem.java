package ru.adaptionwheel.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
import ru.adaptionwheel.data.ModDataComponents;
import ru.adaptionwheel.data.WheelData;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

public class MahoragaWheelItem extends Item implements ICurioItem {

    public static final String WHEEL_SLOT = "wheel";

    public static boolean isWheel(net.minecraft.world.item.ItemStack stack) {
        return stack != null && (stack.is(ModItems.MAHORAGA_WHEEL.get())
                || stack.is(ModItems.MAHORAGA_WHEEL_WOOD.get()));
    }

    /**
     * The wheel in an entity's dedicated Curios slot, or null.
     *
     * <p>Read the slot directly instead of {@code findFirstCurio}/{@code isEquipped}: Curios caches
     * those answers for the current game tick, so equipping and unequipping in the same tick (a
     * command, a swap, a mob dying) leaves every wearer check stale until the next tick. The
     * dedicated slot is the only place this item can legally be worn anyway.
     */
    @Nullable
    public static ItemStack equippedWheel(LivingEntity entity) {
        if (entity == null) {
            return null;
        }
        var handler = CuriosApi.getCuriosInventory(entity)
                .flatMap(inv -> inv.getStacksHandler(WHEEL_SLOT))
                .orElse(null);
        if (handler == null) {
            return null;
        }
        ItemStack stack = handler.getStacks().getStackInSlot(0);
        return isWheel(stack) ? stack : null;
    }

    public MahoragaWheelItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return WHEEL_SLOT.equals(slotContext.identifier());
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return canEquip(slotContext, stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.adaptionwheel.mahoraga_wheel.tooltip").withStyle(ChatFormatting.GOLD));
        tooltipComponents.add(Component.translatable("item.adaptionwheel.mahoraga_wheel.tooltip2").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("item.adaptionwheel.mahoraga_wheel.tooltip3").withStyle(ChatFormatting.DARK_GRAY));

        WheelData wd = stack.get(ModDataComponents.WHEEL_DATA);
        if (wd != null) {
            int count = wd.adaptCount();
            if (count > 0) {
                tooltipComponents.add(Component.translatable("item.adaptionwheel.mahoraga_wheel.adaptations", count)
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }

        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            net.minecraft.client.KeyMapping menu = ru.adaptionwheel.client.AdaptionKeybinds.OPEN_SCREEN_KEY;
            if (menu != null) {
                tooltipComponents.add(Component.translatable(
                                "item.adaptionwheel.mahoraga_wheel.tooltip4",
                                menu.getTranslatedKeyMessage())
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }
}
