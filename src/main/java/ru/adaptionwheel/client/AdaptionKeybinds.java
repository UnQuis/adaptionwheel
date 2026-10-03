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

@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class AdaptionKeybinds {

    public static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "adaptionwheel"));

    public static final KeyMapping OPEN_SCREEN_KEY = new KeyMapping(
            "key.adaptionwheel.open_screen",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_K,
            CATEGORY);

    public static final KeyMapping TOGGLE_INSTABREAK_KEY = new KeyMapping(
            "key.adaptionwheel.toggle_instabreak",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_G,
            CATEGORY);

    private AdaptionKeybinds() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_SCREEN_KEY);
        event.register(TOGGLE_INSTABREAK_KEY);
    }

    public static Component boundKeyName() {
        KeyMapping key = OPEN_SCREEN_KEY;
        return key == null || key.isUnbound() ? null : key.getTranslatedKeyMessage();
    }
}
