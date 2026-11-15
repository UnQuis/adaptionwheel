package ru.adaptionwheel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.ability.ActiveAbility;
import ru.adaptionwheel.ability.ActiveAbilities;
import ru.adaptionwheel.config.AdaptionConfig;

/**
 * Active-ability HUD card beside the hotbar (ported from 1.21.1 to 26.3).
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class AbilityHud {

    private AbilityHud() {
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        if (!showsCard()) {
            return;
        }
        ActiveAbility ability = AbilityAvailability.selected();
        if (ability == null) {
            return;
        }

        GuiGraphicsExtractor g = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        int x = HudCards.x(g.guiWidth(), HudCards.WIDTH);
        int y = HudCards.y(g.guiHeight(), 0, HudCards.HEIGHT);

        int accent = ability.color();
        HudCards.frame(g, x, y, HudCards.WIDTH, HudCards.HEIGHT, accent, WheelUi.CARD, WheelUi.BORDER);
        HudCards.icon(g, x, y, ability.iconStack(), accent);
        HudCards.title(g, mc.font, x, y, ability.name().getString(), WheelUi.INK, HudCards.WIDTH - 38 - 20);
        HudCards.meter(g, x, y, HudCards.WIDTH, HudCards.HEIGHT,
                1f - ClientCooldowns.fraction(ability.id()), accent);
        HudCards.keyChip(g, mc.font, x, y, HudCards.WIDTH, AbilityAvailability.keyLabel(ability), accent);
    }

    static boolean showsCard() {
        if (ActiveAbilities.isEmpty() || !AdaptionConfig.HUD_ENABLED.get()) {
            return false;
        }
        if (AbilityAvailability.selected() == null) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        return mc.gui.screen() == null && mc.player != null
                && !mc.player.isSpectator() && ClientAdaption.wearingWheel;
    }
}
