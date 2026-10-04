package ru.adaptionwheel.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import ru.adaptionwheel.menu.CacheMenu;
import ru.adaptionwheel.server.CacheService;

public class CacheScreen extends AbstractContainerScreen<CacheMenu> {

    private static final int BODY = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int DARK = 0xFF555555;
    private static final int WELL = 0xFF8B8B8B;
    private static final int WELL_TOP = 0xFFA0A0A0;

    public CacheScreen(CacheMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = CacheService.PANEL_WIDTH;
        this.imageHeight = CacheService.PANEL_HEIGHT;
        // Place the "Inventory" label just above the player inventory section
        this.inventoryLabelY = CacheService.PLAYER_INV_Y - 11;
        // Title stays near the top (default titleLabelY = 6 is fine)
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        int right = left + this.imageWidth;
        int bottom = top + this.imageHeight;

        // Outer dark border
        g.fill(left, top, right, bottom, DARK);
        // Main body
        g.fill(left + 1, top + 1, right - 1, bottom - 1, BODY);
        // Top & left light bevel
        g.fill(left + 1, top + 1, right - 1, top + 3, LIGHT);
        g.fill(left + 1, top + 1, left + 3, bottom - 1, LIGHT);

        // Slot wells (drawn for every slot that actually exists in the menu)
        for (var slot : this.menu.slots) {
            int x = left + slot.x - 1;
            int y = top + slot.y - 1;
            g.fill(x, y, x + 18, y + 18, DARK);          // outer
            g.fill(x + 1, y + 1, x + 17, y + 17, WELL);  // inner
            g.fill(x + 1, y + 1, x + 17, y + 2, WELL_TOP); // top highlight
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
