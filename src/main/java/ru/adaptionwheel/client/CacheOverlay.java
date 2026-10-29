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
 * The cache, drawn beside whatever container is open.
 *
 * <p>The cache is player data, not a menu: a crafting table, a chest and a furnace each own their
 * slots and none of them knows the cache exists. Adding a real {@code Slot} to every menu type is
 * not possible in one place — each menu builds its own slot list — so the cache is drawn as a second
 * window next to the open panel and its clicks are sent to the server, which owns the storage.
 *
 * <p>It is a <em>second window</em> rather than an extension under the panel, and that is the whole
 * lesson of the first two attempts: making room by moving {@code topPos} means every screen that
 * positions itself from it — background, slots, labels, the recipe book — has to agree, and one of
 * them disagreeing is a broken screen. A panel beside the container moves nothing vanilla owns.
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class CacheOverlay {

    private static final org.slf4j.Logger LOGGER =
            com.mojang.logging.LogUtils.getLogger();

    /** Vanilla's own label grey, so the cache heading matches every container's. */
    private static final int LABEL_COLOR = 4210752;

    /** Air between the container's panel and the cache's, so the two frames stay distinct. */
    private static final int GAP = 4;

    /** The screen currently carrying the overlay, or null. Identity, not equality: screens rebuild. */
    private static AbstractContainerScreen<?> attached;
    private static int cacheLeft;
    private static int cacheTop;
    /** True when the extension sits directly under the container and shares its seam. */
    private static boolean cacheJoined;
    /** Last placement written to the log, so a per-frame recompute does not spam it. */
    private static String lastPlacement;

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

        attached = screen;
        place(screen);
    }

    /**
     * Recomputes the cache's position from the panel's <em>current</em> box.
     *
     * <p>This runs on every frame rather than once on {@code Init.Post}, and that is not defensive
     * coding: a container screen moves itself after it is initialised. The recipe book, when toggled,
     * shifts {@code leftPos} by 77 pixels through {@code RecipeBookComponent.updateScreenPosition},
     * and a layout captured at init time then points at where the panel used to be - the cache drifts
     * off the panel and the container's own rows appear to spill out of it. Anything that repositions
     * the screen for any reason gets the same treatment for free.
     */
    private static void place(AbstractContainerScreen<?> screen) {
        AbstractContainerScreenAccess access = (AbstractContainerScreenAccess) screen;
        int imageWidth = access.adaptionwheel$getImageWidth();
        int imageHeight = access.adaptionwheel$getImageHeight();
        int left = access.adaptionwheel$getLeftPos();
        int top = access.adaptionwheel$getTopPos();

        // Beside the panel first, and the panel itself is not touched. Lifting the panel to make
        // room below it means fighting every screen that positions itself from topPos - the
        // background, the slots, the labels - and any one of them getting it wrong is a broken
        // screen. A second window next to the first cannot move what the container owns.
        //
        // The left side is tried first because the right side is where vanilla puts the recipe
        // book: with the book open the panel shifts right by 77 pixels and a cache placed on that
        // side lands on top of it.
        //
        // A Curios screen is the one panel that already owns the space to its left: its slots sit
        // at [leftPos - panelWidth, leftPos]. The cache steps over that whole panel rather than
        // covering the slots the player opened the screen for.
        int curioPanel = CuriosCompat.curioPanelWidth(screen);
        int sideTop = Math.max(4, Math.min(top, screen.height - CacheService.CACHE_TEXTURE_HEIGHT - 4));
        int rightSide = left + imageWidth + GAP;
        int leftSide = left - curioPanel - GAP - CacheService.PANEL_WIDTH;
        cacheJoined = false;
        if (leftSide >= 0) {
            cacheLeft = leftSide;
            cacheTop = sideTop;
        } else if (curioPanel == 0 && rightSide + CacheService.PANEL_WIDTH <= screen.width) {
            // On a Curios screen the right side is taken by the status-effect column, so a cache
            // there would cover the effects exactly as it used to cover the slots.
            cacheLeft = rightSide;
            cacheTop = sideTop;
        } else if (screen.height - imageHeight >= CacheService.CACHE_TEXTURE_HEIGHT + 8) {
            // No room beside it, so the panel is lifted and the extension joins it below.
            int newTop = Math.max(4,
                    (screen.height - imageHeight - CacheService.CACHE_TEXTURE_HEIGHT) / 2);
            if (newTop != top) {
                access.adaptionwheel$setTopPos(newTop);
            }
            // Aligned to the panel's right edge, not centred: a panel wider than 176 still reads as
            // the same window, and the usual 176-wide panel lands exactly on its left edge.
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

    /** Before the frame is drawn, so a lifted panel and its cache agree within the same frame. */
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
        place(attached);
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

    /** The cell under the cursor, or -1. The hit box is the slot sprite, which starts one pixel
     *  above and left of the item. */
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
        return row * CacheService.COLUMNS + column;
    }

    private static void detached() {
        attached = null;
        cacheLeft = 0;
        cacheTop = 0;
        cacheJoined = false;
    }
}
