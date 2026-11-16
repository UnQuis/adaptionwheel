package ru.adaptionwheel.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Client-side Curios helpers: overlay offset around Curios screens and a functional button to open
 * Curios from the cache/adapted inventory screen when Curios is present.
 *
 * <p>Curios is optional and not a compile dependency on this branch, so all Curios API/packet access
 * is reflective and must degrade quietly when absent.
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

    /** True if Curios screen support is available on the client classpath. */
    public static boolean isAvailable() {
        try {
            Class.forName("top.theillusivec4.curios.api.client.ICuriosScreen");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
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

    /**
     * Adds a small button that opens Curios from the given inventory-like screen when available.
     * Functional parity aid; icon parity is best-effort/omitted to avoid compile/runtime asset deps.
     */
    public static Button addInventoryButton(AbstractContainerScreen<?> screen, int leftPos, int topPos, boolean recipeBookVisible) {
        if (!isAvailable() || isCurioScreen(screen)) {
            return null;
        }
        int x = buttonX(leftPos, recipeBookVisible);
        int y = buttonY(topPos);
        Button button = Button.builder(Component.literal("C"), b -> tryOpenCurios())
                .bounds(x, y, 14, 14)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("key.curios.open.desc")))
                .build();
        return button;
    }

    public static int buttonX(int leftPos, boolean recipeBookVisible) {
        return recipeBookVisible ? leftPos - 16 : leftPos + 2;
    }

    public static int buttonY(int topPos) {
        return topPos + 2;
    }

    /** Best-effort: ask Curios to open its screen by sending its client->server open packet. */
    public static void tryOpenCurios() {
        try {
            CustomPacketPayload payload = openCurioPacket();
            if (payload == null) {
                return;
            }
            sendToServer(payload);
        } catch (Throwable t) {
            LOGGER.debug("[aw-cache] Could not open Curios screen", t);
        }
    }

    private static CustomPacketPayload openCurioPacket() {
        String[] packetClasses = {
                "top.theillusivec4.curios.common.network.client.CPacketOpenCurios",
                "top.theillusivec4.curios.common.network.client.CPacketOpenVanilla"
        };
        for (String name : packetClasses) {
            try {
                Class<?> cls = Class.forName(name);
                for (Constructor<?> ctor : cls.getConstructors()) {
                    Class<?>[] params = ctor.getParameterTypes();
                    if (params.length == 0) {
                        Object obj = ctor.newInstance();
                        if (obj instanceof CustomPacketPayload p) {
                            return p;
                        }
                    }
                    if (params.length == 1 && params[0] == net.minecraft.world.item.ItemStack.class) {
                        Object obj = ctor.newInstance(net.minecraft.world.item.ItemStack.EMPTY);
                        if (obj instanceof CustomPacketPayload p) {
                            return p;
                        }
                    }
                }
            } catch (ClassNotFoundException ignored) {
                // try next
            } catch (Throwable ignored) {
                // try next
            }
        }
        return null;
    }

    private static void sendToServer(CustomPacketPayload payload) throws Exception {
        Class<?> distributor = Class.forName("net.neoforged.neoforge.client.network.ClientPacketDistributor");
        try {
            Method m = distributor.getMethod("sendToServer", CustomPacketPayload.class, CustomPacketPayload[].class);
            m.invoke(null, payload, new CustomPacketPayload[0]);
            return;
        } catch (NoSuchMethodException ignored) {
        }
        Method m = distributor.getMethod("sendToServer", CustomPacketPayload.class);
        m.invoke(null, payload);
    }
}
