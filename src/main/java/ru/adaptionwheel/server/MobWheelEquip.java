package ru.adaptionwheel.server;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.data.ModDataComponents;
import ru.adaptionwheel.data.WheelData;
import ru.adaptionwheel.item.MahoragaWheelItem;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Equips a wheel on a mob with shift + right click, without opening a GUI or a command.
 *
 * <p>The command stays the administrative path; this is the in-world one. It only accepts a wheel
 * in the main hand so it cannot fire twice from the offhand, and it only accepts a non-player
 * {@link LivingEntity} target so it never competes with the player-to-player adaptation transfer.
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID)
public final class MobWheelEquip {

    private MobWheelEquip() {
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isShiftKeyDown()) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        ItemStack held = event.getItemStack();
        if (!MahoragaWheelItem.isWheel(held)) {
            return;
        }
        if (!(event.getTarget() instanceof LivingEntity mob) || mob instanceof Player) {
            return;
        }
        if (equip(player, mob, held)) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    /**
     * Moves one wheel from the player's hand into the mob's Curios {@code wheel} slot.
     *
     * <p>The stack keeps its {@code wheel_data}, so handing a trained wheel to a mob carries every
     * adaptation with it. A mob whose slot is already occupied is left alone: silently replacing a
     * wheel would destroy its data with no way to recover it.
     */
    public static boolean equip(ServerPlayer player, LivingEntity mob, ItemStack held) {
        var handler = CuriosApi.getCuriosInventory(mob)
                .flatMap(inv -> inv.getStacksHandler(MahoragaWheelItem.WHEEL_SLOT))
                .orElse(null);
        if (handler == null) {
            player.displayClientMessage(Component.literal(
                    "This entity has no 'wheel' Curios slot"), false);
            return false;
        }
        if (MahoragaWheelItem.equippedWheel(mob) != null) {
            player.displayClientMessage(Component.literal(
                    mob.getName().getString() + " is already wearing a wheel"), false);
            return false;
        }

        ItemStack toEquip = held.copy();
        toEquip.setCount(1);
        if (!toEquip.has(ModDataComponents.WHEEL_DATA.get())) {
            toEquip.set(ModDataComponents.WHEEL_DATA.get(), WheelData.EMPTY);
        }
        handler.getStacks().setStackInSlot(0, toEquip);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        player.displayClientMessage(Component.literal(
                "Equipped a wheel on " + mob.getName().getString()), false);
        return true;
    }
}
