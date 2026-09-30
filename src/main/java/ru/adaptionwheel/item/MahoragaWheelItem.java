package ru.adaptionwheel.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import ru.adaptionwheel.data.ModDataComponents;
import ru.adaptionwheel.data.WheelData;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

/** The Mahoraga Wheel — the source of all adaptation. Equips only in the dedicated Curios wheel slot. */
public class MahoragaWheelItem extends Item implements ICurioItem {

    public static final String WHEEL_SLOT = "wheel";

    /**
     * Whether this stack is one of the wheels, wooden or not.
     *
     * <p>Exists because two places now need the answer from an arbitrary stack rather than from
     * the registry: adaptation transfer keys off what is in the hand, and asking by item identity
     * would silently exclude the wooden wheel, which carries the same data component and is the
     * very first thing a player makes.</p>
     */
    public static boolean isWheel(net.minecraft.world.item.ItemStack stack) {
        return stack != null && (stack.is(ModItems.MAHORAGA_WHEEL.get())
                || stack.is(ModItems.MAHORAGA_WHEEL_WOOD.get()));
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
        // Hint for the adaptation browser screen (client-only key name).
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            net.minecraft.client.KeyMapping key = ru.adaptionwheel.client.AdaptionKeybinds.OPEN_SCREEN_KEY;
            if (key != null && !key.isUnbound()) {
                tooltipComponents.add(Component.translatable(
                                "item.adaptionwheel.mahoraga_wheel.tooltip4",
                                key.getTranslatedKeyMessage())
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }
}
