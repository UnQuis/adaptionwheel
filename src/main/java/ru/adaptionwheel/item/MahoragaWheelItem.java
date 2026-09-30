package ru.adaptionwheel.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.fml.loading.FMLEnvironment;
import ru.adaptionwheel.data.ModDataComponents;
import ru.adaptionwheel.data.WheelData;

import java.util.function.Consumer;

/**
 * The Mahoraga Wheel — the source of all adaptation.
 * With Curios installed it equips only in the dedicated {@code wheel} curio slot
 * (see {@link ru.adaptionwheel.compat.CuriosCompat}); without Curios it is "worn"
 * while carried in the inventory or off-hand (see {@link ru.adaptionwheel.compat.WheelSlots}).
 */
public class MahoragaWheelItem extends Item {

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
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag tooltipFlag) {
        tooltip.accept(Component.translatable("item.adaptionwheel.mahoraga_wheel.tooltip").withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.translatable("item.adaptionwheel.mahoraga_wheel.tooltip2").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.adaptionwheel.mahoraga_wheel.tooltip3").withStyle(ChatFormatting.DARK_GRAY));

        WheelData wd = stack.get(ModDataComponents.WHEEL_DATA);
        if (wd != null) {
            int count = wd.adaptCount();
            if (count > 0) {
                tooltip.accept(Component.translatable("item.adaptionwheel.mahoraga_wheel.adaptations", count)
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
        // Hint for the adaptation browser screen (client-only key name).
        if (FMLEnvironment.getDist().isClient()) {
            Component keyName = ru.adaptionwheel.client.AdaptionKeybinds.boundKeyName();
            if (keyName != null) {
                tooltip.accept(Component.translatable("item.adaptionwheel.mahoraga_wheel.tooltip4", keyName)
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }
}
