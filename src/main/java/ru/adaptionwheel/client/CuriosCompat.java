package ru.adaptionwheel.client;

import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal client-side Curios helpers for the cache overlay/inventory hook.
 *
 * <p>Overlay placement must step around Curios' own left-side panel when a Curios screen is open.
 * The full 1.21.1 helper also owns the inventory button/packet wiring used by CacheScreen; that is
 * intentionally omitted here to avoid colliding with CacheScreen work while still keeping foreign
 * container overlay placement correct.
 */
public final class CuriosCompat {

    /** The narrowest panel Curios can draw: one column, 14 + 18 * 1. */
    private static final int NARROWEST_CURIO_PANEL = 14 + 18;

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** The public {@code panelWidth} field per screen class; the value inside is null if absent. */
    private static final Map<Class<?>, PanelWidthField> PANEL_WIDTH = new HashMap<>();

    private record PanelWidthField(Field field) {
    }

    private CuriosCompat() {
    }

    /** True for Curios' own inventory screen, which draws its slots to the left of its panel. */
    public static boolean isCurioScreen(Screen screen) {
        if (screen == null) {
            return false;
        }
        try {
            Class<?> curiosScreen = Class.forName("top.theillusivec4.curios.api.client.ICuriosScreen");
            return curiosScreen.isInstance(screen);
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    /**
     * How far a Curios screen's slot panel reaches to the left of its {@code leftPos}, or 0.
     */
    public static int curioPanelWidth(Screen screen) {
        if (!isCurioScreen(screen)) {
            return 0;
        }
        PanelWidthField cached = PANEL_WIDTH.computeIfAbsent(screen.getClass(), type -> {
            try {
                return new PanelWidthField(type.getField("panelWidth"));
            } catch (NoSuchFieldException e) {
                LOGGER.warn("[aw-cache] {} has no panelWidth field; assuming a single curio column",
                        type.getSimpleName());
                return new PanelWidthField(null);
            }
        });
        if (cached.field() == null) {
            return NARROWEST_CURIO_PANEL;
        }
        try {
            int width = cached.field().getInt(screen);
            return width > 0 ? width : NARROWEST_CURIO_PANEL;
        } catch (IllegalAccessException e) {
            return NARROWEST_CURIO_PANEL;
        }
    }
}
