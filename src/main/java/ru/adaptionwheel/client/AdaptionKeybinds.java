package ru.adaptionwheel.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;
import ru.adaptionwheel.AdaptionWheel;

/** Keybind for the adaptation browser screen (default: K). */
@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class AdaptionKeybinds {

    /** Null on dedicated servers; only dereferenced behind a dist check. */
    public static final KeyMapping OPEN_SCREEN_KEY = new KeyMapping(
            "key.adaptionwheel.open_screen",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.adaptionwheel");

    private AdaptionKeybinds() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SCREEN_KEY);
    }
}
