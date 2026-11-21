package ru.adaptionwheel.server;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
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
 * Equips or removes a wheel with shift + right click on a mob, without a GUI or command.
 *
 * <p>The command stays the administrative path; this is the in-world one. It only accepts the main
 * hand so it cannot fire twice, and it only accepts a non-player {@link LivingEntity} target so it
 * never competes with the player-to-player adaptation transfer. An owned mob can only be wheeled by
 * its owner: handing a trained wheel to somebody else's dog is griefing, not a feature.
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
        if (!(event.getTarget() instanceof LivingEntity mob) || mob instanceof Player) {
            return;
        }
        if (mob instanceof OwnableEntity ownable && ownable.getOwner() instanceof Entity owner
                && owner != player) {
            player.displayClientMessage(Component.translatableWithFallback(
                    "adaptionwheel.msg.mobwheel_not_owner",
                    "You can only put a wheel on your own tamed mob"), false);
            return;
        }

        ItemStack held = event.getItemStack();
        boolean done;
        if (MahoragaWheelItem.isWheel(held)) {
            done = equip(player, mob, held);
        } else if (held.isEmpty()) {
            done = unequip(player, mob);
        } else {
            return;
        }
        if (done) {
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
            player.displayClientMessage(Component.translatableWithFallback(
                    "adaptionwheel.msg.mobwheel_no_slot", "This entity has no 'wheel' Curios slot"), false);
            return false;
        }
        if (MahoragaWheelItem.equippedWheel(mob) != null) {
            player.displayClientMessage(Component.translatable(
                    "adaptionwheel.msg.mobwheel_occupied", mob.getName()), false);
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
        player.displayClientMessage(Component.translatable(
                "adaptionwheel.msg.mobwheel_equipped", mob.getName()), false);
        return true;
    }

    /** Shift + right click with an empty hand takes the wheel back, saving its data first. */
    public static boolean unequip(ServerPlayer player, LivingEntity mob) {
        var handler = CuriosApi.getCuriosInventory(mob)
                .flatMap(inv -> inv.getStacksHandler(MahoragaWheelItem.WHEEL_SLOT))
                .orElse(null);
        ItemStack worn = handler == null ? null : handler.getStacks().getStackInSlot(0);
        if (worn == null || !MahoragaWheelItem.isWheel(worn)) {
            return false;
        }
        // The attachment is the live copy; the stack is what survives the mob. Write before removal.
        AdaptionEvents.saveToStack(worn, Wearers.data(mob));
        handler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
        if (!player.getInventory().add(worn)) {
            player.drop(worn, false);
        }
        player.displayClientMessage(Component.translatableWithFallback(
                "adaptionwheel.msg.mobwheel_removed", "Removed the wheel from %s", mob.getName()), false);
        return true;
    }
}
