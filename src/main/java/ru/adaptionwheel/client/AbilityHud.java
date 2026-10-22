package ru.adaptionwheel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.ability.ActiveAbility;
import ru.adaptionwheel.ability.ActiveAbilities;
import ru.adaptionwheel.config.AdaptionConfig;

/**
 * The small dark active-ability card that rides beside the hotbar.
 *
 * <p>It is deliberately invisible until {@link ActiveAbilities} contains something. A placeholder
 * HUD is worse than no HUD: it occupies the same corner, teaches the player to ignore it, and then
 * has to be re-learned when the first real ability arrives. The menu's reserved card is where the
 * "coming soon" belongs; this surface exists only to display an actual ability.
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

        GuiGraphics graphics = event.getGuiGraphics();
        Minecraft minecraft = Minecraft.getInstance();
        int x = HudCards.x(graphics.guiWidth(), HudCards.WIDTH);
        int y = HudCards.y(graphics.guiHeight(), 0, HudCards.HEIGHT);

        int accent = ability.color();
        HudCards.frame(graphics, x, y, HudCards.WIDTH, HudCards.HEIGHT, accent,
                WheelUi.CARD, WheelUi.BORDER);
        HudCards.icon(graphics, x, y, ability.iconStack(), accent);
        HudCards.title(graphics, minecraft.font, x, y, ability.name().getString(),
                WheelUi.INK, HudCards.WIDTH - 38 - 20);

        // The bar refills as the server-side cooldown runs out. It is a mirror, not the authority:
        // a dropped packet shortens a bar, never the wait.
        HudCards.meter(graphics, x, y, HudCards.WIDTH, HudCards.HEIGHT,
                1f - ClientCooldowns.fraction(ability.id()), accent);

        HudCards.keyChip(graphics, minecraft.font, x, y, HudCards.WIDTH,
                AbilityAvailability.keyLabel(ability), accent);
    }

    /**
     * Whether the ability card occupies the slot closest to the hotbar. The Instabreak readout
     * stacks above it when it does, which is why this is asked from outside rather than kept
     * private: two independent readouts claiming the same corner is how they end up overlapping.
     */
    static boolean showsCard() {
        if (ActiveAbilities.isEmpty() || !AdaptionConfig.ABILITY_HUD_ENABLED.get()) {
            return false;
        }
        // Nothing unlocked draws nothing, so it must also claim no slot: the Instabreak card sits
        // one card higher when this one is present, and a card that never appears would leave a
        // hole in the stack.
        if (AbilityAvailability.selected() == null) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.screen == null && minecraft.player != null && !minecraft.options.hideGui
                && !minecraft.player.isSpectator() && ClientAdaption.wearingWheel;
    }
}
