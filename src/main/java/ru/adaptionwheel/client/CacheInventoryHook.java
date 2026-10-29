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
 * Routes the player's normal inventory key into the adapted inventory.
 *
 * <p>The replacement happens here rather than in a new key mapping because the request is
 * specifically that {@code E} opens the inventory with the extra slots: a second key would leave
 * the normal inventory one keystroke away and the cache looking like a separate screen again. The
 * server still owns the menu, so this only cancels the client-side vanilla screen and asks for the
 * cache menu; the client never fabricates slots locally.
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
        // Curios' own button leaves its screen by opening the vanilla inventory and sending
        // CPacketOpenVanilla in the same click. Redirecting that opening here would ask for the
        // cache menu too, and the two server-side menu opens would race; the vanilla inventory is
        // the answer that matches the packet the button already sent.
        if (CuriosCompat.isCurioScreen(minecraft.screen)) {
            return;
        }
        // Creative's inventory is a different screen with item tabs; replacing it would remove
        // those. Creative players can still open the cache with V.
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
