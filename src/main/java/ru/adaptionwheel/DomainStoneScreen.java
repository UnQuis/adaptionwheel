package ru.adaptionwheel;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import ru.adaptionwheel.menu.DomainStoneMenu;

/**
 * The Domain Stone's screen.
 *
 * <p>Nothing but a name: the layout is {@link TradeScreen}'s, and the two trading blocks differ only
 * in which adaptations their menu can produce.</p>
 */
public class DomainStoneScreen extends TradeScreen<DomainStoneMenu> {

    public DomainStoneScreen(DomainStoneMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
