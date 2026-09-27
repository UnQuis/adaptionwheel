package ru.adaptionwheel.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import ru.adaptionwheel.AdaptionWheel;

/** Keybind for the adaptation browser screen (default: K). */
@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class AdaptionKeybinds {

    /** Category label key: {@code key.category.adaptionwheel.adaptionwheel} (26.x derives it from the id). */
    public static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "adaptionwheel"));

    /** Null on dedicated servers; only dereferenced behind a dist check. */
    public static final KeyMapping OPEN_SCREEN_KEY = new KeyMapping(
            "key.adaptionwheel.open_screen",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_K, // 26.3 uses SDL scancodes (GLFW is gone)
            CATEGORY);

    /**
     * Instabreak stance toggle. Only meaningful once the fist has reached Netherite level 8;
     * the server re-validates the request either way.
     */
    public static final KeyMapping TOGGLE_INSTABREAK_KEY = new KeyMapping(
            "key.adaptionwheel.toggle_instabreak",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_G, // 26.3 uses SDL scancodes (GLFW is gone)
            CATEGORY);

    private AdaptionKeybinds() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_SCREEN_KEY);
        event.register(TOGGLE_INSTABREAK_KEY);
    }

    /** Translated name of the bound key, or null when unbound. Client only. */
    public static Component boundKeyName() {
        KeyMapping key = OPEN_SCREEN_KEY;
        return key == null || key.isUnbound() ? null : key.getTranslatedKeyMessage();
    }
}
