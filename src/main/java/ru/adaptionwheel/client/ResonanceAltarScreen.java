package ru.adaptionwheel.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import ru.adaptionwheel.menu.ResonanceAltarMenu;

/**
 * The Resonance Altar's trade screen.
 *
 * <p>The layout is {@link TradeScreen}'s. What this adds is the one thing the altar has to say that
 * the stone does not: whose adaptations are on offer, because a bone belongs to five kinds of
 * skeleton and the list has to make it plain which of them a row is about.</p>
 */
public class ResonanceAltarScreen extends TradeScreen<ResonanceAltarMenu> {

    public ResonanceAltarScreen(ResonanceAltarMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    /**
     * A different empty state from the stone's.
     *
     * <p>The altar's list can be empty with an item in the slot, which means "you have already
     * learned everything this mob teaches" — a real answer, and a different one from "put
     * something in". The stone can only ever be the second, which is why it gets the fallback
     * wording.</p>
     */
    @Override
    protected Component hintFor() {
        if (!menu.hasOffering()) {
            return Component.translatable("adaptionwheel.gui.need_item");
        }
        return Component.translatable("adaptionwheel.gui.all_learned");
    }
}
