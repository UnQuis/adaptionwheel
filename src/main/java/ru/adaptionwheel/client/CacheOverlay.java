package ru.adaptionwheel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
 * The cache, drawn beside whatever container is open (26.3 overlay flow).
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class CacheOverlay {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static final int LABEL_COLOR = 4210752;
    private static final int GAP = 4;

    private static AbstractContainerScreen<?> attached;
    private static int cacheLeft;
    private static int cacheTop;
    private static boolean cacheJoined;
    private static String lastPlacement;

    private CacheOverlay() {
    }

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post event) {
        detached();
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen
                || screen instanceof CacheScreen) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !AdaptionConfig.ENABLE_INVENTORY_ADAPTATION.get()
                || !ClientAdaption.active(Concepts.ENV_INVENTORY)) {
            return;
        }

        attached = screen;
        place(screen);
    }

    private static void place(AbstractContainerScreen<?> screen) {
        AbstractContainerScreenAccess access = (AbstractContainerScreenAccess) screen;
        int imageWidth = access.adaptionwheel$getImageWidth();
        int imageHeight = access.adaptionwheel$getImageHeight();
        int left = access.adaptionwheel$getLeftPos();
        int top = access.adaptionwheel$getTopPos();

        int curioPanel = CuriosCompat.curioPanelWidth(screen);
        int sideTop = Math.max(4, Math.min(top, screen.height - CacheService.CACHE_TEXTURE_HEIGHT - 4));
        int rightSide = left + imageWidth + GAP;
        int leftSide = left - curioPanel - GAP - CacheService.PANEL_WIDTH;
        cacheJoined = false;
        if (leftSide >= 0) {
            cacheLeft = leftSide;
            cacheTop = sideTop;
        } else if (curioPanel == 0 && rightSide + CacheService.PANEL_WIDTH <= screen.width) {
            cacheLeft = rightSide;
            cacheTop = sideTop;
        } else if (screen.height - imageHeight >= CacheService.CACHE_TEXTURE_HEIGHT + 8) {
            int newTop = Math.max(4,
                    (screen.height - imageHeight - CacheService.CACHE_TEXTURE_HEIGHT) / 2);
            if (newTop != top) {
                access.adaptionwheel$setTopPos(newTop);
            }
            cacheLeft = left + imageWidth - CacheService.PANEL_WIDTH;
            cacheTop = newTop + imageHeight;
            cacheJoined = true;
        } else {
            cacheLeft = Integer.MIN_VALUE;
            cacheTop = Integer.MIN_VALUE;
        }
        logPlacement(screen, imageWidth, imageHeight, left, top);
    }

    private static void logPlacement(AbstractContainerScreen<?> screen, int imageWidth, int imageHeight,
                                     int left, int top) {
        String key = screen.getClass().getSimpleName() + " " + imageWidth + "x" + imageHeight
                + " " + left + "," + top + " " + cacheLeft + "," + cacheTop + " " + cacheJoined;
        if (key.equals(lastPlacement)) {
            return;
        }
        lastPlacement = key;
        LOGGER.info("[aw-cache] {} panel {}x{} at {},{} -> cache at {},{} joined={} screen {}x{}",
                screen.getClass().getSimpleName(), imageWidth, imageHeight, left, top,
                cacheLeft, cacheTop, cacheJoined, screen.width, screen.height);
    }

    @SubscribeEvent
    public static void onRenderPre(ScreenEvent.Render.Pre event) {
        if (attached != null && event.getScreen() == attached) {
            place(attached);
        }
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (attached == null || event.getScreen() != attached) {
            return;
        }
        place(attached);
        if (cacheLeft == Integer.MIN_VALUE) {
            return;
        }
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        Minecraft minecraft = Minecraft.getInstance();

        if (cacheJoined) {
            CachePanel.drawJoined(graphics, cacheLeft, cacheTop);
        } else {
            CachePanel.draw(graphics, cacheLeft, cacheTop);
        }
        graphics.text(minecraft.font, Component.translatable("adaptionwheel.gui.cache"),
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
                graphics.item(stack, x, y);
                graphics.itemDecorations(minecraft.font, stack, x, y);
            }
        }

        // Redraw carried stack on top of the overlay.
        ItemStack carried = minecraft.player == null ? ItemStack.EMPTY
                : minecraft.player.containerMenu.getCarried();
        if (!carried.isEmpty()) {
            graphics.pose().pushMatrix();
            graphics.item(carried, event.getMouseX() - 8, event.getMouseY() - 8);
            graphics.itemDecorations(minecraft.font, carried,
                    event.getMouseX() - 8, event.getMouseY() - 8);
            graphics.pose().popMatrix();
        }

        if (hovered >= 0) {
            ItemStack stack = ClientCache.get(hovered);
            if (!stack.isEmpty()) {
                graphics.setTooltipForNextFrame(minecraft.font, stack, event.getMouseX(), event.getMouseY());
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
        place(attached);
        int index = indexAt(event.getMouseX(), event.getMouseY());
        if (index < 0) {
            return;
        }
        int button = event.getButton();
        // 26.3 reports left click as button 1; server click semantics expect 0=left, 1=right.
        if (button == 1 || button == 0) {
            int serverButton = (button == 1) ? 0 : 1;
            CacheClickPayload.send(index, serverButton, Screen.hasShiftDown());
        }
        event.setCanceled(true);
    }

    public static boolean blocksOutsideClick(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        if (screen != attached) {
            return false;
        }
        place(screen);
        if (cacheLeft == Integer.MIN_VALUE) {
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

    private static int indexAt(double mouseX, double mouseY) {
        if (attached == null || cacheLeft == Integer.MIN_VALUE) {
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
        int index = row * CacheService.COLUMNS + column;
        return index < CacheService.SLOTS ? index : -1;
    }

    private static void detached() {
        attached = null;
        cacheLeft = 0;
        cacheTop = 0;
        cacheJoined = false;
    }
}
