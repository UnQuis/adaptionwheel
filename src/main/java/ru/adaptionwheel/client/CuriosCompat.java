package ru.adaptionwheel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import top.theillusivec4.curios.api.client.ICuriosScreen;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * The three pieces of Curios this mod needs that its API does not expose.
 *
 * <p>The cache screen replaces the vanilla inventory, and Curios only ever adds its button to
 * {@code InventoryScreen} itself — so without this the player loses the way into the curio slots
 * the moment the cache is active. Curios' API jar ships {@link ICuriosScreen} and the item handler
 * but not the button, the screen class or the packet that opens it, and putting the full jar on the
 * compile classpath to reach three members would tie this build to Curios' internals just as hard
 * while pretending not to.
 *
 * <p>So the contact surface is deliberately tiny and every lookup is optional: a missing class, a
 * renamed field or a disabled config all degrade to "no button" instead of an exception at screen
 * init. Curios is a required dependency, so this is about version drift, not absence.
 */
public final class CuriosCompat {

    private static final String SCREEN_CLASS = "top.theillusivec4.curios.client.gui.CuriosScreen";
    private static final String PACKET_CLASS =
            "top.theillusivec4.curios.common.network.client.CPacketOpenCurios";
    private static final String CONFIG_CLASS = "top.theillusivec4.curios.client.CuriosClientConfig";

    /** Curios' own button sprites, resolved through the GUI atlas like every other sprite. */
    public static final ResourceLocation BUTTON_SPRITE =
            ResourceLocation.fromNamespaceAndPath("curios", "button");
    public static final ResourceLocation BUTTON_SPRITE_HIGHLIGHTED =
            ResourceLocation.fromNamespaceAndPath("curios", "button_highlighted");

    /** The narrowest panel Curios can draw: one column, 14 + 18 * 1. */
    private static final int NARROWEST_CURIO_PANEL = 14 + 18;

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static boolean resolved;
    private static Method buttonOffset;
    private static Constructor<?> openPacket;
    private static boolean buttonEnabled = true;

    /** The public {@code panelWidth} field per screen class; the value inside is null if absent. */
    private static final Map<Class<?>, PanelWidthField> PANEL_WIDTH = new HashMap<>();

    private record PanelWidthField(Field field) {
    }

    private CuriosCompat() {
    }

    /** True for Curios' own inventory screen, which draws its slots to the left of its panel. */
    public static boolean isCurioScreen(Screen screen) {
        return screen instanceof ICuriosScreen;
    }

    /**
     * How far a Curios screen's slot panel reaches to the left of its {@code leftPos}, or 0.
     *
     * <p>The cache is normally placed there, and a Curios screen is the one panel in the game that
     * already occupies that space. Reading the screen's own public {@code panelWidth} is what makes
     * the cache step over it instead of covering the slots the player opened the screen for.
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
            // renderBg assigns panelWidth, so before the first frame it is still 0. A zero would
            // put the cache straight on top of the slots this exists to step over, so treat it as
            // the narrowest real panel rather than as "no panel".
            return width > 0 ? width : NARROWEST_CURIO_PANEL;
        } catch (IllegalAccessException e) {
            return NARROWEST_CURIO_PANEL;
        }
    }

    /**
     * The button's x relative to the panel's left edge, config-aware.
     *
     * <p>Delegating to Curios' {@code getButtonOffset} rather than hardcoding 24 keeps the player's
     * chosen corner and fine offsets working. The {@code -2} is Curios' own placement arithmetic,
     * copied because there is no exported equivalent.
     */
    public static int buttonX() {
        return offset().getA() - 2;
    }

    /** The button's y relative to the panel's top edge, config-aware. */
    public static int buttonY() {
        return offset().getB() + 81;
    }

    private static Tuple<Integer, Integer> offset() {
        resolve();
        if (buttonOffset != null) {
            try {
                @SuppressWarnings("unchecked")
                Tuple<Integer, Integer> offset = (Tuple<Integer, Integer>) buttonOffset.invoke(null, false);
                if (offset != null) {
                    return offset;
                }
            } catch (ReflectiveOperationException e) {
                LOGGER.warn("[aw-cache] could not read the Curios button offset: {}", e.toString());
                buttonOffset = null;
            }
        }
        // The default TOP_LEFT corner, already through Curios' -2 / +81 placement.
        return new Tuple<>(24, 6);
    }

    /** Whether the button should exist at all: Curios must be loaded and its own button enabled. */
    public static boolean buttonAvailable() {
        resolve();
        return buttonEnabled && openPacket != null;
    }

    /**
     * Opens the curio screen, exactly the way Curios' own inventory button does.
     *
     * <p>The carried stack has to be lifted off the current menu before the switch: the server
     * moves the carried stack of the menu it is closing, and leaving it there would duplicate it.
     * It travels inside the packet instead, which is how Curios re-seats it on the new menu.
     */
    public static void openCurios() {
        resolve();
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (openPacket == null || player == null) {
            return;
        }
        ItemStack carried = player.containerMenu.getCarried();
        player.containerMenu.setCarried(ItemStack.EMPTY);
        try {
            Object packet = openPacket.newInstance(carried);
            if (!(packet instanceof CustomPacketPayload payload)) {
                // Something that is not a payload means Curios changed shape under us. Put the
                // stack back rather than eat it: losing what the player was holding is the one
                // failure here they cannot undo.
                player.containerMenu.setCarried(carried);
                return;
            }
            PacketDistributor.sendToServer(payload);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("[aw-cache] could not open the curio screen: {}", e.toString());
            player.containerMenu.setCarried(carried);
        }
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            Class<?> screen = Class.forName(SCREEN_CLASS);
            buttonOffset = screen.getMethod("getButtonOffset", boolean.class);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("[aw-cache] Curios screen layout is unavailable; the cache screen will have"
                    + " no curio button ({})", e.toString());
            buttonOffset = null;
        }
        try {
            Class<?> packet = Class.forName(PACKET_CLASS);
            openPacket = packet.getConstructor(ItemStack.class);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("[aw-cache] Curios' open-screen packet is unavailable; the cache screen will"
                    + " have no curio button ({})", e.toString());
            openPacket = null;
        }
        try {
            Class<?> config = Class.forName(CONFIG_CLASS);
            Object client = config.getField("CLIENT").get(null);
            Object enable = client.getClass().getField("enableButton").get(client);
            buttonEnabled = (Boolean) enable.getClass().getMethod("get").invoke(enable);
        } catch (ReflectiveOperationException e) {
            // A missing config field is not worth a warning: the button is harmless either way.
            buttonEnabled = true;
        }
    }
}
