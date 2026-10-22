package ru.adaptionwheel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.mixin.AbstractContainerScreenAccess;
import ru.adaptionwheel.network.CacheClickPayload;
import ru.adaptionwheel.server.CacheService;

/**
 * The cache, drawn under whatever container is open.
 *
 * <p>The cache is player data, not a menu: a crafting table, a chest and a furnace each own their
 * slots and none of them knows the cache exists. Adding a real {@code Slot} to every menu type is
 * not possible in one place — each menu builds its own slot list — so the cache is drawn as an
 * extension of the open panel and its clicks are sent to the server, which owns the storage.
 *
 * <p>That extension needs room, so the container is moved up: {@code topPos} is what positions the
 * background, the slots and the labels alike, and moving it is the one change that keeps all three
 * consistent. The player's own inventory is excluded — it is already the cache screen, with real
 * slots.
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class CacheOverlay {

    /** Vanilla's own label grey, so the cache heading matches every container's. */
    private static final int LABEL_COLOR = 4210752;

    /** The screen currently carrying the overlay, or null. Identity, not equality: screens rebuild. */
    private static AbstractContainerScreen<?> attached;
    private static int cacheLeft;
    private static int cacheTop;
    /** True when the extension sits directly under the container and shares its seam. */
    private static boolean cacheJoined;

    private CacheOverlay() {
    }

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post event) {
        detached();
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        // The player's own inventory is the cache screen: it has real slots for these cells, and
        // creative's has item tabs the extension would cover.
        if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen
                || screen instanceof CacheScreen) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !AdaptionConfig.ENABLE_INVENTORY_ADAPTATION.get()
                || !ClientAdaption.active(Concepts.ENV_INVENTORY)) {
            return;
        }

        AbstractContainerScreenAccess access = (AbstractContainerScreenAccess) screen;
        int imageWidth = access.adaptionwheel$getImageWidth();
        int imageHeight = access.adaptionwheel$getImageHeight();
        int total = imageHeight + CacheService.CACHE_TEXTURE_HEIGHT;
        if (total <= screen.height) {
            // There is room under the panel: lift the panel so the two together sit centred.
            int top = Math.max(4, (screen.height - total) / 2);
            access.adaptionwheel$setTopPos(top);
            cacheLeft = access.adaptionwheel$getLeftPos() + (imageWidth - CacheService.PANEL_WIDTH) / 2;
            cacheTop = top + imageHeight;
            cacheJoined = true;
        } else {
            // A tall panel (a six-row chest, say) leaves no vertical room at all. The cache then
            // goes beside it rather than off the bottom of the screen, and if neither side fits it
            // is simply not shown -- a panel the player cannot reach is worse than no panel.
            int left = access.adaptionwheel$getLeftPos();
            int y = Math.max(4, (screen.height - CacheService.CACHE_TEXTURE_HEIGHT) / 2);
            if (left + imageWidth + 6 + CacheService.PANEL_WIDTH <= screen.width) {
                cacheLeft = left + imageWidth + 6;
                cacheTop = y;
            } else if (left - 6 - CacheService.PANEL_WIDTH >= 0) {
                cacheLeft = left - 6 - CacheService.PANEL_WIDTH;
                cacheTop = y;
            } else {
                return;
            }
        }
        attached = screen;
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (attached == null || event.getScreen() != attached) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        Minecraft minecraft = Minecraft.getInstance();

        if (cacheJoined) {
            CachePanel.drawJoined(graphics, cacheLeft, cacheTop);
        } else {
            CachePanel.draw(graphics, cacheLeft, cacheTop);
        }
        graphics.drawString(minecraft.font, Component.translatable("adaptionwheel.gui.cache"),
                cacheLeft + 8, cacheTop + 4, LABEL_COLOR, false);

        int hovered = indexAt(event.getMouseX(), event.getMouseY());
        for (int index = 0; index < CacheService.SLOTS; index++) {
            int x = cellX(index);
            int y = cellY(index);
            if (index == hovered) {
                graphics.fill(x - 1, y - 1, x + 17, y + 17, 0x80FFFFFF);
            }
            ItemStack stack = ClientCache.get(index);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x, y);
                graphics.renderItemDecorations(minecraft.font, stack, x, y);
            }
        }

        // The panel and the carried stack are drawn by the screen before this event runs, so the
        // stack the player is holding would sit *under* the extension and disappear exactly while
        // they are moving something into it. Redrawing it is the only way to keep it on top.
        ItemStack carried = minecraft.player == null ? ItemStack.EMPTY
                : minecraft.player.containerMenu.getCarried();
        if (!carried.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(0f, 0f, 232f);
            graphics.renderItem(carried, event.getMouseX() - 8, event.getMouseY() - 8);
            graphics.renderItemDecorations(minecraft.font, carried,
                    event.getMouseX() - 8, event.getMouseY() - 8);
            graphics.pose().popPose();
        }

        if (hovered >= 0) {
            ItemStack stack = ClientCache.get(hovered);
            if (!stack.isEmpty()) {
                graphics.renderTooltip(minecraft.font, stack, event.getMouseX(), event.getMouseY());
            }
        }
    }

    @SubscribeEvent
    public static void onClosing(ScreenEvent.Closing event) {
        if (event.getScreen() == attached) {
            detached();
        }
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (attached == null || event.getScreen() != attached) {
            return;
        }
        int index = indexAt(event.getMouseX(), event.getMouseY());
        if (index < 0) {
            return;
        }
        // Buttons 0 and 1 are the two the server understands; anything else (middle, extra) is
        // swallowed rather than falling through to vanilla as a click on the world.
        if (event.getButton() == 0 || event.getButton() == 1) {
            CacheClickPayload.send(index, event.getButton(), Screen.hasShiftDown());
        }
        event.setCanceled(true);
    }

    /**
     * Whether a point lies on the extension rather than outside the panel.
     *
     * <p>Vanilla treats anything past {@code imageHeight} as "outside" and turns such a click into
     * a throw of the carried stack. Without this the first drag over the cache would drop the item
     * the player was holding into the world.
     */
    public static boolean blocksOutsideClick(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        if (screen != attached) {
            return false;
        }
        return mouseX >= cacheLeft && mouseY >= cacheTop
                && mouseX < cacheLeft + CacheService.PANEL_WIDTH
                && mouseY < cacheTop + CacheService.CACHE_TEXTURE_HEIGHT;
    }

    private static int cellX(int index) {
        return cacheLeft + CacheService.slotX(index % CacheService.COLUMNS);
    }

    private static int cellY(int index) {
        return cacheTop + CacheService.CACHE_TOP_PAD + (index / CacheService.COLUMNS) * CacheService.SLOT;
    }

    /** The cell under the cursor, or -1. The hit box is the slot sprite, which starts one pixel
     *  above and left of the item. */
    private static int indexAt(double mouseX, double mouseY) {
        if (attached == null) {
            return -1;
        }
        int relX = (int) Math.floor(mouseX) - cacheLeft - CacheService.slotX(0) + 1;
        int relY = (int) Math.floor(mouseY) - cacheTop - CacheService.CACHE_TOP_PAD + 1;
        if (relX < 0 || relY < 0) {
            return -1;
        }
        int column = relX / CacheService.SLOT;
        int row = relY / CacheService.SLOT;
        if (column >= CacheService.COLUMNS || row >= CacheService.ROWS) {
            return -1;
        }
        return row * CacheService.COLUMNS + column;
    }

    private static void detached() {
        attached = null;
        cacheLeft = 0;
        cacheTop = 0;
        cacheJoined = false;
    }
}
