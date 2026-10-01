package ru.adaptionwheel.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.menu.DomainStoneMenu;
import ru.adaptionwheel.network.DomainStoneActionPayload;
import ru.adaptionwheel.server.DomainExchange;

import java.util.List;

/**
 * The Domain Stone's screen.
 *
 * <p>Laid out from the sketch: the offering item at the top left, the wheel below it in the slot
 * marked with a cross, a levels bar under that, and the adaptations on offer down the right.</p>
 *
 * <h2>Where the "Minecraft look" comes from</h2>
 *
 * <p>Not from a hand-drawn imitation. The panel and bars reuse {@link AdaptationScreen}'s primitives
 * and palette, whose values were read out of {@code textures/gui/container/generic_54.png}; the two
 * input slots are drawn with that texture's own slot-well pixels, sampled rather than guessed; and
 * the cross on the empty wheel slot is vanilla's own {@code container/beacon/cancel} sprite, the one
 * vanilla uses to say <em>this is missing</em>. So this screen and the adaptation browser obey the
 * same rules and read as one mod.</p>
 *
 * <p>Nothing is blitted except that one cross, and that is not squeamishness. {@code blitSprite}
 * blits a whole sprite with no way to take an 18×18 window out of a 108×19 strip, and the
 * seven-argument {@code blit} hardcodes a 256×256 sheet — so a sprite is reachable only at its own
 * size, which is exactly the cross and nothing else on this screen.</p>
 *
 * <h2>26.3 notes, because the drawing pipeline was rewritten here too</h2>
 *
 * <p>{@code render}/{@code renderBg}/{@code renderTooltip} are gone: everything is
 * {@code extract*(GuiGraphicsExtractor, ...)} now, and {@code AbstractContainerScreen} calls
 * {@code extractContents} then the carried item then {@code extractTooltip} itself, so overriding
 * {@code extractContents} and calling super gives the right ordering for free. Text is
 * {@code g.text} and {@code g.centeredText}, and {@code blitSprite} takes the pipeline first. Input
 * is event objects: {@code mouseClicked(MouseButtonEvent, boolean)}. And
 * {@code imageWidth}/{@code imageHeight} are final, so the size goes to {@code super} rather than
 * being assigned.</p>
 *
 * <p>Drawing happens in absolute screen coordinates, after {@code extractContents} has popped the
 * (leftPos, topPos) translation back off — so every call adds {@code leftPos}/{@code topPos} itself,
 * which is why they are passed in explicitly rather than relied on.</p>
 *
 * <h2>The screen sends intent, not state</h2>
 *
 * <p>Clicking a row, dragging the slider and pressing the button each send a payload and draw
 * nothing locally. Everything visible is whatever the server last said, which is why the row under
 * the cursor and the row the server would grant cannot disagree.</p>
 */
public class DomainStoneScreen extends AbstractContainerScreen<DomainStoneMenu> {

    // ---- vanilla palette, as sampled out of textures/gui/container/generic_54.png
    private static final int PANEL_EDGE = 0xFF000000;
    private static final int PANEL_HILIGHT = 0xFFFFFFFF;
    private static final int PANEL_BODY = 0xFFC6C6C6;

    private static final int BAR_FRAME = 0xFF09100C;
    private static final int BAR_EMPTY = 0xFF28332D;
    private static final int BAR_FULL = 0xFF71A549;

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

    private static final Identifier CANCEL_CROSS =
            Identifier.withDefaultNamespace("container/beacon/cancel");

    private static final int BAR_H = 5;
    private static final int ROW_H = 12;
    private static final int PAD = 7;
    /** Rows visible in the list: the list's body height, in whole rows. */
    private static final int LIST_ROWS = (DomainStoneMenu.LIST_H - 16) / ROW_H;
    private static final int LIST_BODY_Y = 15;
    /** Space a drawn scrollbar takes out of the row text. */
    private static final int SCROLLER_GAP = 9;

    private boolean draggingSlider;
    private int scroll;

    public DomainStoneScreen(DomainStoneMenu menu, Inventory inventory, Component title) {
        // Both sizes go to super: they are final here, so assigning them afterwards is not an option.
        super(menu, inventory, title, DomainStoneMenu.IMAGE_WIDTH, DomainStoneMenu.IMAGE_HEIGHT);
        this.inventoryLabelY = DomainStoneMenu.INVENTORY_LABEL_Y;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractContents(g, mouseX, mouseY, partial);

        int x = this.leftPos;
        int y = this.topPos;

        // The window itself. Vanilla's own container background is 176x166 and this is 176x229, so
        // it cannot be blitted over the top -- and a background the player inventory floats on is
        // not a Minecraft-style screen, it is a grid hanging in the void.
        panel(g, x, y, this.imageWidth, this.imageHeight);

        g.text(this.font, this.title, x + PAD, y + PAD, TEXT_HEADER, false);

        // Drawn at the slots' own coordinates, read rather than repeated -- see the note on the
        // layout block in DomainStoneMenu.
        slotWell(g, x + DomainStoneMenu.OFFER_X, y + DomainStoneMenu.OFFER_Y);
        slotWell(g, x + DomainStoneMenu.WHEEL_X, y + DomainStoneMenu.WHEEL_Y);
        if (!menu.hasWheel()) {
            // Vanilla's cross for "this is missing", over the one slot that has to be filled.
            g.blitSprite(RenderPipelines.GUI_TEXTURED, CANCEL_CROSS,
                    x + DomainStoneMenu.WHEEL_X, y + DomainStoneMenu.WHEEL_Y, 18, 18);
        }

        renderSlider(g, x + DomainStoneMenu.SLIDER_X, y + DomainStoneMenu.SLIDER_Y);
        renderButton(g, x + DomainStoneMenu.BUTTON_X, y + DomainStoneMenu.BUTTON_Y, mouseX, mouseY);
        renderList(g, x + DomainStoneMenu.LIST_X, y + DomainStoneMenu.LIST_Y,
                DomainStoneMenu.LIST_W, DomainStoneMenu.LIST_H, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // Nothing: each slot is labelled by what it is, not by a caption over it.
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

    private void renderSlider(GuiGraphicsExtractor g, int x, int y) {
        String concept = menu.selectedConcept();
        int max = concept == null ? 1 : DomainExchange.maxLevelsFor(concept);
        int w = DomainStoneMenu.SLIDER_W;
        g.fill(x, y, x + w, y + BAR_H, BAR_FRAME);
        g.fill(x + 1, y + 1, x + w - 1, y + BAR_H - 1, BAR_EMPTY);
        int filled = Math.round((w - 2) * (Math.min(menu.levels(), max) / (float) max));
        if (filled > 0) {
            g.fill(x + 1, y + 1, x + 1 + filled, y + BAR_H - 1, BAR_FULL);
        }
        // Both captions are short on purpose. A 60-wide column cannot hold "Cost: 5 x Nether Star",
        // and the item is already sitting in the slot directly above, so the only thing worth
        // saying is how many are needed.
        g.text(this.font, Component.translatable("adaptionwheel.gui.levels", menu.levels(), max),
                x, y + BAR_H + 4, TEXT_DIM, false);
        if (concept != null) {
            g.text(this.font, Component.translatable("adaptionwheel.gui.needs", menu.currentCost()),
                    x, y + BAR_H + 15, menu.canAfford() ? TEXT : TEXT_SHORT, false);
        }
    }

    private void renderButton(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
        int w = DomainStoneMenu.BUTTON_W;
        int h = DomainStoneMenu.BUTTON_H;
        boolean hot = within(mouseX, mouseY, x, y, w, h);
        boolean on = menu.canAfford();
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
            Component hint = menu.recipe() == null
                    ? Component.translatable("adaptionwheel.gui.need_item")
                    : Component.translatable("adaptionwheel.gui.nothing_left");
            g.text(this.font, clipped(hint, w - 12), rowX, top + 2, TEXT_DIM, false);
            return;
        }

        g.enableScissor(x + 1, top, x + w - 1, y + h - 1);
        for (int row = 0; row < rows && row + scroll < pool.size(); row++) {
            int index = row + scroll;
            String concept = pool.get(index);
            int ry = top + row * ROW_H;
            boolean selected = index == menu.selectedIndex();
            boolean hot = mouseX >= rowX && mouseX < rowX + textW
                    && mouseY >= ry && mouseY < ry + ROW_H;
            if (selected) {
                // The concept's own colour, so the per-concept grouping the HUD teaches still reads
                // against a light background. AdaptationScreen scales these to 52% for the same
                // reason; here it is stronger because a 12px row has less to carry it.
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

    /** Scrollbar, drawn for the same reason the bars are: see {@link AdaptationScreen}. */
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
     * onto the {@code Font}, which is why {@link AdaptationScreen} trims with
     * {@code plainSubstrByWidth} rather than passing a limit. Names come out of the lang file, so a
     * translation longer than the column is a real possibility rather than a theoretical one.</p>
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

        if (within(mx, my, x + DomainStoneMenu.SLIDER_X, y + DomainStoneMenu.SLIDER_Y,
                DomainStoneMenu.SLIDER_W, BAR_H + 2)) {
            draggingSlider = true;
            sliderTo(mx - x - DomainStoneMenu.SLIDER_X);
            return true;
        }
        if (within(mx, my, x + DomainStoneMenu.BUTTON_X, y + DomainStoneMenu.BUTTON_Y,
                DomainStoneMenu.BUTTON_W, DomainStoneMenu.BUTTON_H)) {
            DomainStoneActionPayload.exchange();
            return true;
        }
        // The scroller's gutter is excluded: it is drawn over the row area but selects nothing, so
        // clicking it must not quietly choose whatever row happens to be under the cursor.
        int rowsW = DomainStoneMenu.LIST_W - (maxScroll() > 0 ? SCROLLER_GAP : 0);
        if (within(mx, my, x + DomainStoneMenu.LIST_X, y + DomainStoneMenu.LIST_Y + LIST_BODY_Y,
                rowsW, LIST_ROWS * ROW_H)) {
            int row = (int) ((my - y - DomainStoneMenu.LIST_Y - LIST_BODY_Y) / ROW_H) + scroll;
            if (row < menu.candidates().size()) {
                DomainStoneActionPayload.select(row);
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingSlider) {
            sliderTo(event.x() - this.leftPos - DomainStoneMenu.SLIDER_X);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingSlider = false;
        return super.mouseReleased(event);
    }

    /**
     * Turns a pixel offset into a level count and sends it.
     *
     * <p>Sent only when the integer changes. A drag crosses the same pixel column many times and
     * there are eight levels, so this is eight packets for a whole sweep rather than one per mouse
     * move. It is not clamped here either — the server decides what a level count may be, and
     * clamping in two places is how the two drift apart.</p>
     */
    private void sliderTo(double offsetPx) {
        String concept = menu.selectedConcept();
        if (concept == null) {
            return;
        }
        int max = DomainExchange.maxLevelsFor(concept);
        int usable = DomainStoneMenu.SLIDER_W - 2;
        double clamped = Math.max(0, Math.min(offsetPx, usable));
        int wanted = 1 + (int) Math.round(clamped / usable * (max - 1));
        if (wanted != menu.levels()) {
            DomainStoneActionPayload.levels(wanted);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (mx >= this.leftPos + DomainStoneMenu.LIST_X
                && mx < this.leftPos + DomainStoneMenu.LIST_X + DomainStoneMenu.LIST_W) {
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