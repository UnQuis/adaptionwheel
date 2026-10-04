package ru.adaptionwheel.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import ru.adaptionwheel.menu.CacheMenu;
import ru.adaptionwheel.server.CacheService;

public class CacheScreen extends AbstractContainerScreen<CacheMenu> {

    private static final int BODY = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int DARK = 0xFF555555;
    private static final int WELL_TOP = 0xFFA0A0A0;
    private static final int WELL = 0xFF8B8B8B;

    public CacheScreen(CacheMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, CacheService.PANEL_WIDTH, CacheService.PANEL_HEIGHT);
        // Place the "Inventory" label just above the player inventory section. Derived rather
        // than a literal, because PANEL_HEIGHT moved when the hotbar gained its vanilla gap.
        this.inventoryLabelY = CacheService.PLAYER_INV_Y - 11;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.fill(0, 0, this.imageWidth, this.imageHeight, DARK);
        g.fill(1, 1, this.imageWidth - 1, this.imageHeight - 1, BODY);
        g.fill(1, 1, this.imageWidth - 1, 3, LIGHT);
        g.fill(1, 1, 3, this.imageHeight - 1, LIGHT);

        for (var slot : this.menu.slots) {
            int x = slot.x - 1;
            int y = slot.y - 1;
            g.fill(x, y, x + 18, y + 18, DARK);
            g.fill(x + 1, y + 1, x + 17, y + 17, WELL);
            g.fill(x + 1, y + 1, x + 17, y + 2, WELL_TOP);
        }

        super.extractLabels(g, mouseX, mouseY);
    }
}