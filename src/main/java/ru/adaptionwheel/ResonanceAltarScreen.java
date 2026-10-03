package ru.adaptionwheel;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import ru.adaptionwheel.menu.ResonanceAltarMenu;

/**
 * The Resonance Altar's trade screen.
 *
 * <p>The layout is {@link TradeScreen}'s. What this adds is the one thing the altar has to say that
 * the stone does not: whose adaptations are on offer, because a bone belongs to five kinds of
 * skeleton and the list has to make it plain which of them a row is about.</p>
 *
 * <p>The empty states come from {@link TradeScreen} unchanged: no wheel in the slot, or an item that
 * drops from nothing. An adaptation the altar <em>already sold this wheel</em> is not an empty state
 * any more — its row stays, and the exchange is the thing that refuses it.</p>
 */
public class ResonanceAltarScreen extends TradeScreen<ResonanceAltarMenu> {

    public ResonanceAltarScreen(ResonanceAltarMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
