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
        if (ActiveAbilities.isEmpty() || !AdaptionConfig.ABILITY_HUD_ENABLED.get()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null || minecraft.options.hideGui
                || minecraft.player.isSpectator() || !ClientAdaption.wearingWheel) {
            return;
        }
        ActiveAbility ability = AbilityAvailability.selected();
        if (ability == null) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        int cardWidth = 104;
        int cardHeight = 34;

        int hotbarRight = screenWidth / 2 + 91;
        int x = Math.min(screenWidth - cardWidth - 4, hotbarRight + 8);
        int y = screenHeight - cardHeight - 4;
        if (x < hotbarRight + 4 && x + cardWidth > screenWidth / 2 - 91) {
            // A narrow GUI cannot fit a card beside the hotbar. Lift it above the hotbar rather
            // than letting it cover the item the player is holding.
            x = screenWidth - cardWidth - 4;
            y = screenHeight - cardHeight - 26;
        }

        int accent = ability.color();
        WheelUi.card(graphics, x, y, cardWidth, cardHeight, 8, WheelUi.CARD, WheelUi.BORDER);
        graphics.fill(x + 4, y + 8, x + 6, y + cardHeight - 8, accent);

        WheelUi.fillRounded(graphics, x + 9, y + 7, 20, 20, 5,
                WheelUi.tint(accent));
        WheelUi.itemIcon(graphics, ability.iconStack(), x + 11, y + 9, 16);

        String keyLabel = AbilityAvailability.keyLabel(ability);
        int keyWidth = keyLabel.isBlank() ? 0 : 16;        int nameMax = cardWidth - 38 - keyWidth;
        WheelUi.text(graphics, minecraft.font, WheelUi.MONO_8,
                WheelUi.fit(minecraft.font, WheelUi.MONO_8,
                        WheelUi.upper(ability.name().getString()), nameMax),
                x + 34, y + 5, WheelUi.INK, false);

        // The bar refills as the server-side cooldown runs out. It is a mirror, not the authority:
        // a dropped packet shortens a bar, never the wait.
        int barX = x + 34;
        int barW = cardWidth - 44;
        float recovered = 1f - ClientCooldowns.fraction(ability.id());
        graphics.fill(barX, y + 22, barX + barW, y + 24, 0x24FFFFFF);
        int filled = Math.round(barW * recovered);
        if (filled > 0) {
            graphics.fill(barX, y + 22, barX + filled, y + 23,
                    WheelUi.withAlpha(accent, 0.55f));
        }

        if (!keyLabel.isBlank()) {
            int keyX = x + cardWidth - 20;
            WheelUi.fillRounded(graphics, keyX, y + 8, 14, 14, 4,
                    WheelUi.withAlpha(accent, 0.14f));
            WheelUi.centered(graphics, minecraft.font, WheelUi.MONO_8, keyLabel,
                    keyX + 7, y + 11, accent, false);
        }
    }
}
