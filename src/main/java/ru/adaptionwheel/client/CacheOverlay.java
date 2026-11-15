package ru.adaptionwheel.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * 26.3 cache-overlay integration point.
 *
 * <p>The full 1.21.1 overlay (drawn beside arbitrary container screens via ScreenEvents and
 * GuiGraphics) depends on 1.21.1 screen/render APIs that differ on 26.3. This stub preserves the
 * mixin hook contract so {@code CachePanelClickMixin} can remain wired without reverting 26.3
 * screen/render changes; full overlay rendering/input parity can be layered on top once ported to
 * the 26.3 screen event and extraction APIs.
 */
public final class CacheOverlay {

    private CacheOverlay() {
    }

    public static boolean blocksOutsideClick(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        return false;
    }
}
