package ru.adaptionwheel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.network.OpenCachePayload;

/**
 * Routes the player's normal inventory key into the adapted inventory (26.3).
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class CacheInventoryHook {

    private CacheInventoryHook() {
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof InventoryScreen)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gameMode == null) {
            return;
        }
        // Avoid racing Curios' own screen transitions.
        if (CuriosCompat.isCurioScreen(minecraft.screen)) {
            return;
        }
        if (minecraft.gameMode.hasInfiniteItems()) {
            return;
        }
        if (!ClientAdaption.active(Concepts.ENV_INVENTORY)) {
            return;
        }

        event.setNewScreen(null);
        OpenCachePayload.send();
    }
}
