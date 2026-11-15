package ru.adaptionwheel.client;

/**
 * Placeholder for 26.3 cache panel rendering helpers.
 *
 * <p>1.21.1 drew the cache extension with {@code GuiGraphics} and vanilla slot sprites from a
 * shared overlay. 26.3 screen rendering uses different extraction/render APIs, so the full panel
 * draw helpers are intentionally not copied verbatim here to avoid reverting 26.3-specific
 * rendering code. The class exists as the parity anchor for future 26.3-adapted overlay work.
 */
public final class CachePanel {

    private CachePanel() {
    }
}
