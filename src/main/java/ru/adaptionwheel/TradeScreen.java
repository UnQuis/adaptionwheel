package ru.adaptionwheel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.menu.TradeMenu;
import ru.adaptionwheel.network.TradeActionPayload;
import ru.adaptionwheel.server.DomainExchange;

import java.util.List;

/**
 * The trading screen, shared by the Domain Stone and the Resonance Altar.
 *
 * <p>Laid out from the sketch: the offering item at the top left, the wheel below it in the slot
 * marked with the wheel icon, a bar under that, and the adaptations on offer down the right.</p>
 *
 * <p>Shared by both blocks, for the same reason {@code TradeMenu} is: two copies of a drawing
 * routine is two copies of a bug. Subclasses supply the title and, optionally, the wording for an
 * empty list.</p>
 *
 * <p>Generic in the menu, and that is not decoration. {@code AbstractContainerScreen} implements
 * {@code MenuAccess<T>}, which declares {@code T getMenu()} and is <em>invariant</em>, so a screen
 * shared by two menu types has to be {@code MenuAccess} in both of them or
 * {@code RegisterMenuScreensEvent} refuses the registration with a type error. One type parameter
 * is what satisfies both.</p>
 *
 * <h2>Why everything is drawn in {@code extractLabels}</h2>
 *
 * <p>That hook runs after the background and <em>before</em> the slots, inside the
 * {@code pushMatrix/translate(leftPos, topPos)} block. That is the only window in which a slot's
 * background can be drawn under the item that goes in it:</p>
 *
 * <ul>
 *   <li>vanilla does <b>not</b> draw slot wells. {@code extractSlot} draws the contents only — the
 *       wells are baked into a background texture, and 26.3 no longer blits one for a container
 *       screen. So a screen that draws its own panel must draw every well itself, or the items sit
 *       on bare grey. That is exactly what the first version of this screen did not do, and the
 *       player inventory came out invisible.</li>
 *   <li>coordinates here are already relative to {@code leftPos}/{@code topPos}, so nothing adds
 *       them — and the mouse has to be offset by hand for hit testing.</li>
 *   <li>{@code super.extractLabels} draws the title and "Inventory", so it is called <em>last</em>:
 *       its text has to land on top of the panel, not under it.</li>
 * </ul>
 *
 * <h2>Where the "Minecraft look" comes from</h2>
 *
 * <p>Not a hand-drawn imitation. Slot wells are {@code generic_54.png}'s own pixels, sampled. The
 * panel, bars and scroller reuse {@link ru.adaptionwheel.client.AdaptationScreen}'s primitives and palette, so the two
 * screens read as one mod. The wheel icon in the empty wheel slot is the Curio slot icon the mod
 * already ships, {@code textures/slot/empty_wheel_slot.png} — the same picture a player sees in
 * their Curios wheel slot, which is the point: it says "the wheel goes here" in the one place the
 * mod has already taught them to look.</p>
 *
 * <h2>26.3 notes</h2>
 *
 * <p>There is no {@code render}/{@code renderBg}/{@code renderTooltip}: everything is
 * {@code extract*(GuiGraphicsExtractor, ...)}. Text is {@code g.text} and {@code g.centeredText},
 * and input is event objects — {@code mouseClicked(MouseButtonEvent, boolean)}, whose second argument
 * is "double click" and not a button index. {@code imageWidth}/{@code imageHeight} are final and go
 * to {@code super}. A scaled single-texture blit is
 * {@code blit(Identifier, x0, y0, x1, y1, u0, u1, v0, v1)} with normalised UVs — which is how the
 * 32×32 Curio icon reaches a 16×16 well.</p>
 */
public class TradeScreen<T extends TradeMenu> extends AbstractContainerScreen<T> {

    // ---- vanilla palette, as sampled out of textures/gui/container/generic_54.png
    private static final int PANEL_EDGE = 0xFF000000;
    private static final int PANEL_HILIGHT = 0xFFFFFFFF;
    private static final int PANEL_BODY = 0xFFC6C6C6;

    private static final int BAR_FRAME = 0xFF09100C;
    private static final int BAR_EMPTY = 0xFF28332D;
    private static final int BAR_FULL = 0xFF71A549;
    private static final int BAR_SHORT = 0xFF9A4B4B;

    // The chest GUI's own slot well, pixel for pixel: a #373737 frame, an #8B8B8B body, and a white
    // bottom-and-right inner shadow.
    private static final int SLOT_FRAME = 0xFF373737;
    private static final int SLOT_BODY = 0xFF8B8B8B;
    private static final int SLOT_SHADOW = 0xFFFFFFFF;

    private static final int TEXT = 0xFF404040;
    private static final int TEXT_DIM = 0xFF707070;
    private static final int TEXT_HEADER = 0xFF3F3F3F;
    private static final int TEXT_SHORT = 0xFFA02020;
    private static final int ROW_HOVER = 0x80FFFFFF;

    private static final Identifier WHEEL_SLOT_ICON =
            Identifier.fromNamespaceAndPath(ru.adaptionwheel.AdaptionWheel.MODID,
                    "textures/slot/empty_wheel_slot.png");

    private static final int BAR_H = 5;
    private static final int ROW_H = 12;
    private static final int PAD = 7;
    /** Rows visible in the list: the list's body height, in whole rows. */
    private static final int LIST_ROWS = (TradeMenu.LIST_H - 16) / ROW_H;
    private static final int LIST_BODY_Y = 15;
    /** Space a drawn scrollbar takes out of the row text. */
    private static final int SCROLLER_GAP = 9;

    private int scroll;

    public TradeScreen(T menu, Inventory inventory, Component title) {
        // Both sizes go to super: they are final here, so assigning them afterwards is not an option.
        super(menu, inventory, title, TradeMenu.IMAGE_WIDTH, TradeMenu.IMAGE_HEIGHT);
        this.inventoryLabelY = TradeMenu.INVENTORY_LABEL_Y;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // Coordinates are relative to (leftPos, topPos) from here on.
        panel(g, 0, 0, this.imageWidth, this.imageHeight);

        // Every slot in the menu, not just the two of ours: vanilla draws none of them.
        for (Slot slot : menu.slots) {
            slotWell(g, slot.x, slot.y);
        }
        if (!menu.hasWheel()) {
            // The Curio slot icon, 32x32 drawn into a 16x16 well.
            g.blit(WHEEL_SLOT_ICON, TradeMenu.WHEEL_X + 1, TradeMenu.WHEEL_Y + 1,
                    TradeMenu.WHEEL_X + 17, TradeMenu.WHEEL_Y + 17, 0f, 1f, 0f, 1f);
        }

        renderExperience(g, TradeMenu.SLIDER_X, TradeMenu.SLIDER_Y);
        renderButton(g, TradeMenu.BUTTON_X, TradeMenu.BUTTON_Y, mouseX, mouseY);
        renderList(g, TradeMenu.LIST_X, TradeMenu.LIST_Y,
                TradeMenu.LIST_W, TradeMenu.LIST_H, mouseX, mouseY);

        // Title and "Inventory" land on top of the panel we just drew.
        super.extractLabels(g, mouseX, mouseY);
    }

    /** The window's own name. Overridden by the altar, which is a different block. */
    protected Component titleFor() {
        return Component.translatable(menu.titleKey());
    }

    /**
     * What the list says when it has nothing to show.
     *
     * <p>Two empty states that look identical and mean opposite things — nothing was offered, or
     * everything that was offered has been learned — so the block answers for itself.</p>
     */
    protected Component hintFor() {
        return menu.hasOffering()
                ? Component.translatable("adaptionwheel.gui.nothing_left")
                : Component.translatable("adaptionwheel.gui.need_item");
    }

    /** The chest GUI's slot well, six fills, exact. */
    private void slotWell(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, SLOT_FRAME);
        g.fill(x + 1, y, x + 18, y + 1, SLOT_BODY);
        g.fill(x, y + 17, x + 1, y + 18, SLOT_BODY);
        g.fill(x + 17, y + 1, x + 18, y + 17, SLOT_SHADOW);
        g.fill(x + 1, y + 17, x + 18, y + 18, SLOT_SHADOW);
        g.fill(x + 1, y + 1, x + 17, y + 17, SLOT_BODY);
    }

    /**
     * The player's own experience, against what the selected adaptation costs.
     *
     * <p>Not an input. The bar is a readout, because the price is a property of the adaptation —
     * one level for something one-time, up to four for a boss or a mutation — and a draggable
     * control over a number nobody may choose is a control that lies.</p>
     */
    private void renderExperience(GuiGraphicsExtractor g, int x, int y) {
        int playerLevel = playerLevel();
        int price = menu.xpPrice();
        int w = TradeMenu.SLIDER_W;
        boolean affordable = menu.canAfford(playerLevel);

        g.fill(x, y, x + w, y + BAR_H, BAR_FRAME);
        g.fill(x + 1, y + 1, x + w - 1, y + BAR_H - 1, BAR_EMPTY);
        if (price > 0) {
            int filled = Math.round((w - 2) * Math.min(1f, price / (float) Math.max(1, playerLevel)));
            if (filled > 0) {
                g.fill(x + 1, y + 1, x + 1 + filled, y + BAR_H - 1, affordable ? BAR_FULL : BAR_SHORT);
            }
        }
        g.text(this.font, Component.translatable("adaptionwheel.gui.your_level", playerLevel),
                x, y + BAR_H + 4, TEXT_DIM, false);
        if (menu.selectedConcept() != null) {
            g.text(this.font, Component.translatable("adaptionwheel.gui.price", price),
                    x, y + BAR_H + 15, affordable ? TEXT : TEXT_SHORT, false);
        }
    }

    /** The player's experience level, read from the client's own copy. */
    private int playerLevel() {
        return Minecraft.getInstance().player == null ? 0 : Minecraft.getInstance().player.experienceLevel;
    }

    private void renderButton(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
        int w = TradeMenu.BUTTON_W;
        int h = TradeMenu.BUTTON_H;
        // The mouse is still in screen coordinates here, so the hit area is offset by hand.
        boolean hot = within(mouseX - this.leftPos, mouseY - this.topPos, x, y, w, h);
        boolean on = menu.canAfford(playerLevel());
        panel(g, x, y, w, h);
        if (on) {
            g.fill(x + 2, y + 2, x + w - 2, y + h - 2, hot ? ROW_HOVER : 0x60FFFFFF);
        }
        g.centeredText(this.font, Component.translatable("adaptionwheel.gui.exchange"),
                x + w / 2, y + 5, on ? TEXT_HEADER : TEXT_DIM);
    }

    private void renderList(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY) {
        panel(g, x, y, w, h);
        List<String> pool = menu.candidates();
        int top = y + LIST_BODY_Y;
        int listH = h - LIST_BODY_Y - 1;
        int rows = Math.max(1, LIST_ROWS);
        int maxScroll = Math.max(0, pool.size() - rows);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        // Rows live inside the bevel, so the hit area is inset to match: a row narrower than the
        // one above it selects on a pixel the player cannot see. And when the scroller will be
        // drawn, the text stops short of it rather than running underneath.
        int rowX = x + 4;
        int textW = w - 8 - (maxScroll > 0 ? SCROLLER_GAP : 0);

        g.text(this.font, Component.translatable("adaptionwheel.gui.choose"),
                rowX, y + 6, TEXT_HEADER, false);

        if (pool.isEmpty()) {
            g.text(this.font, clipped(hintFor(), w - 12), rowX, top + 2, TEXT_DIM, false);
            return;
        }

        g.enableScissor(x + 1, top, x + w - 1, y + h - 1);
        for (int row = 0; row < rows && row + scroll < pool.size(); row++) {
            int index = row + scroll;
            String concept = pool.get(index);
            int ry = top + row * ROW_H;
            boolean selected = index == menu.selectedIndex();
            boolean hot = within(mouseX - this.leftPos, mouseY - this.topPos, rowX, ry, textW, ROW_H);
            if (selected) {
                // The concept's own colour, so the per-concept grouping the HUD teaches still reads
                // against a light background.
                g.fill(rowX, ry, rowX + textW, ry + ROW_H, tint(Concepts.color(concept), 0.69f));
            } else if (hot) {
                g.fill(rowX, ry, rowX + textW, ry + ROW_H, ROW_HOVER);
            }
            g.text(this.font, clipped(Concepts.displayName(concept), textW - 3),
                    rowX + 2, ry + 2, selected ? TEXT_HEADER : TEXT, false);
        }
        g.disableScissor();

        if (maxScroll > 0) {
            scroller(g, x + w - 7, top, 5, listH, maxScroll, rows);
        }
    }

    /** Scrollbar, drawn for the same reason the bars are: see {@link ru.adaptionwheel.client.AdaptationScreen}. */
    private void scroller(GuiGraphicsExtractor g, int x, int y, int w, int h, int maxScroll, int rows) {
        g.fill(x, y, x + w, y + h, BAR_FRAME);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, BAR_EMPTY);
        int knobH = Math.max(8, h * rows / (rows + maxScroll));
        int knobY = y + 1 + (h - 2 - knobH) * scroll / maxScroll;
        g.fill(x + 1, knobY, x + w - 1, knobY + knobH, BAR_FULL);
    }

    private void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        if (w < 8 || h < 8) {
            return;
        }
        g.fill(x, y, x + w, y + 1, PANEL_EDGE);
        g.fill(x, y + h - 1, x + w, y + h, PANEL_EDGE);
        g.fill(x, y, x + 1, y + h, PANEL_EDGE);
        g.fill(x + w - 1, y, x + w, y + h, PANEL_EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + 3, PANEL_HILIGHT);
        g.fill(x + 1, y + h - 3, x + w - 1, y + h - 1, PANEL_HILIGHT);
        g.fill(x + 1, y + 1, x + 3, y + h - 1, PANEL_HILIGHT);
        g.fill(x + w - 3, y + 1, x + w - 1, y + h - 1, PANEL_HILIGHT);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, PANEL_BODY);
    }

    /** The panel body, moved {@code strength} of the way towards {@code rgb}. */
    private static int tint(int rgb, float strength) {
        int r = (int) (0xC6 + (0xFF - 0xC6) * strength * (rgb >> 16 & 0xFF) / 255f);
        int gg = (int) (0xC6 + (0xFF - 0xC6) * strength * (rgb >> 8 & 0xFF) / 255f);
        int b = (int) (0xC6 + (0xFF - 0xC6) * strength * (rgb & 0xFF) / 255f);
        return 0xFF000000 | r << 16 | gg << 8 | b;
    }

    /**
     * A concept name trimmed to fit, with an ellipsis when it does not.
     *
     * <p>Neither branch has a max-width overload on {@code text}/{@code drawString} — it was moved
     * onto the {@code Font}. Names come out of the lang file, so a translation longer than the column
     * is a real possibility rather than a theoretical one.</p>
     */
    private String clipped(Component text, int maxWidth) {
        String plain = text.getString();
        if (this.font.width(plain) <= maxWidth) {
            return plain;
        }
        return this.font.plainSubstrByWidth(plain, Math.max(0, maxWidth - this.font.width("..."))) + "...";
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int x = this.leftPos;
        int y = this.topPos;
        double mx = event.x();
        double my = event.y();
        // mouseClicked's second argument is "double click", not a button index: the button lives on
        // the event. Reading it off the event is the whole of the migration from the 1.21.1
        // (double, double, int) signature.
        if (event.button() != 0) {
            return super.mouseClicked(event, doubleClick);
        }
        if (within(mx, my, x + TradeMenu.BUTTON_X, y + TradeMenu.BUTTON_Y,
                TradeMenu.BUTTON_W, TradeMenu.BUTTON_H)) {
            TradeActionPayload.exchange();
            return true;
        }
        // The scroller's gutter is excluded: it is drawn over the row area but selects nothing, so
        // clicking it must not quietly choose whatever row happens to be under the cursor.
        int rowsW = TradeMenu.LIST_W - (maxScroll() > 0 ? SCROLLER_GAP : 0);
        if (within(mx, my, x + TradeMenu.LIST_X, y + TradeMenu.LIST_Y + LIST_BODY_Y,
                rowsW, LIST_ROWS * ROW_H)) {
            int row = (int) ((my - y - TradeMenu.LIST_Y - LIST_BODY_Y) / ROW_H) + scroll;
            if (row < menu.candidates().size()) {
                TradeActionPayload.select(row);
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (mx >= this.leftPos + TradeMenu.LIST_X
                && mx < this.leftPos + TradeMenu.LIST_X + TradeMenu.LIST_W) {
            int maxScroll = maxScroll();
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    /** Rows past the visible page, i.e. whether a scrollbar is drawn at all. */
    private int maxScroll() {
        return Math.max(0, menu.candidates().size() - Math.max(1, LIST_ROWS));
    }

    private static boolean within(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}