package ru.adaptionwheel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.item.SwordOfExterminationItem;
import ru.adaptionwheel.network.FireSlashPayload;

/** Sends empty-air sword swings to the server so cursed slashes fire on every click. */
@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class ClientSwingHandler {

    private ClientSwingHandler() {
    }

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        if (isCursedSword()) {
            FireSlashPayload.send();
        }
    }

    private static boolean isCursedSword() {
        Minecraft mc = Minecraft.getInstance();
        ItemStack held = mc.player != null ? mc.player.getMainHandItem() : ItemStack.EMPTY;
        return held.getItem() instanceof SwordOfExterminationItem && SwordOfExterminationItem.isCursed(held);
    }
}
