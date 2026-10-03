package ru.adaptionwheel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.client.AdaptationScreen;
import ru.adaptionwheel.client.MouseButtons;
import ru.adaptionwheel.menu.ResonanceAltarMenu;
import ru.adaptionwheel.menu.TradeMenu;
import ru.adaptionwheel.network.TradeActionPayload;

import java.util.List;

/**
 * The Resonance Altar's screen, and the mod's only container screen.
 *
 * <p>It was generic over the menu, because the Domain Stone and the Resonance Altar each had one and
 * both needed the same screen. There is one menu now, so there is nothing to be generic over: the
 * class names {@link ResonanceAltarMenu} outright, and the per-block subclass that existed only to
 * supply a type argument is gone. Layout constants still come from {@link TradeMenu}, so a slot and
 * the well drawn behind it cannot drift apart.</p>
 *
 * <p>What the polish pass changed against the first version:</p>
 * <ul>
 *   <li><b>The list is a sunken well.</b> Dark inset body, light-on-dark rows with a stripe in the
 *       concept's own colour, zebra shading, a bright outline on the selected row. The colour
 *       grouping now reads at a glance instead of only on selection.</li>
 *   <li><b>A real vanilla-style button</b> with three states (disabled / idle / hovered), a bevel,
 *       white hover outline, click sound and an explanatory tooltip when it is disabled.</li>
 *   <li><b>Experience readout</b> with a lit fill, quarter ticks, a highlight row and an XP orb
 *       next to the price.</li>
 *   <li><b>The empty wheel slot breathes</b>: a soft golden pulse around the well tells the player
 *       where the wheel goes before they have read anything.</li>
 *   <li><b>A draggable scrollbar</b> with a bevelled knob, a result counter in the list header and
 *       a wrapped, centred empty-state message.</li>
 *   <li>Hit areas now match what is drawn, to the pixel (rows used to start 4px left of the
 *       visible row).</li>
 * </ul>
 *
 * <p>New lang keys: {@code adaptionwheel.gui.select_first}, {@code adaptionwheel.gui.not_enough_levels}.</p>
 *
 * <p>The price line carries the item cost as well as the experience, because a purchase is one level
 * of an adaptation and both halves of what that costs grow as the adaptation climbs — see
 * {@link ru.adaptionwheel.server.DomainExchange#priceForLevel}.</p>
 *
 * <p><b>Everything is drawn in {@code extractLabels}, in coordinates relative to
 * {@code (leftPos, topPos)}.</b> On 26.3 that hook runs inside the translate vanilla pushes before
 * the slots, so it is the one window in which a slot well can be drawn under its own item — and
 * adding {@code leftPos} again puts the whole panel at double the offset, shoved into the
 * bottom-right corner with the wells twice as far from the items as the items are from each other.
 * The mouse arrives in screen coordinates and is offset by hand where it is hit-tested. See also
 * {@link AdaptationScreen}.</p>
 */
public class TradeScreen extends AbstractContainerScreen<ResonanceAltarMenu> {

    // ---- vanilla palette, sampled out of textures/gui/container/generic_54.png
    private static final int PANEL_EDGE = 0xFF000000;
    private static final int PANEL_HILIGHT = 0xFFFFFFFF;
    private static final int PANEL_BODY = 0xFFC6C6C6;

    private static final int SLOT_FRAME = 0xFF373737;
    private static final int SLOT_BODY = 0xFF8B8B8B;
    private static final int SLOT_SHADOW = 0xFFFFFFFF;

    // ---- experience bar
    private static final int BAR_FRAME = 0xFF09100C;
    private static final int BAR_EMPTY = 0xFF28332D;
    private static final int BAR_FULL = 0xFF71A549;
    private static final int BAR_FULL_HI = 0xFFB4F26B;
    private static final int BAR_SHORT = 0xFF9A4B4B;
    private static final int BAR_SHORT_HI = 0xFFD98080;

    // ---- the list well
    private static final int LIST_BG = 0xFF1E1E24;
    private static final int ROW_ZEBRA = 0x0FFFFFFF;
    private static final int ROW_HOVER = 0x26FFFFFF;
    private static final int ROW_TEXT = 0xFFC8C8C8;
    private static final int ROW_TEXT_SELECTED = 0xFFFFFFFF;

    // ---- button
    private static final int BTN_BODY = 0xFF707070;
    private static final int BTN_BODY_HOT = 0xFF6F7FB8;
    private static final int BTN_BODY_OFF = 0xFF4A4A4A;
    private static final int BTN_LIGHT = 0xFFA8A8A8;
    private static final int BTN_LIGHT_OFF = 0xFF5C5C5C;
    private static final int BTN_DARK = 0xFF383838;

    private static final int TEXT = 0xFF404040;
    private static final int TEXT_DIM = 0xFF707070;
    private static final int TEXT_HEADER = 0xFF3F3F3F;
    private static final int TEXT_SHORT = 0xFFA02020;

    /** The mod's Curio wheel-slot icon, 32x32, drawn into a 16x16 well. */
    private static final Identifier WHEEL_SLOT_ICON = Identifier.fromNamespaceAndPath(
            ru.adaptionwheel.AdaptionWheel.MODID, "textures/slot/empty_wheel_slot.png");

    private static final int BAR_H = 5;
    private static final int ROW_H = 12;
    private static final int PAD = 7;
    /** Rows visible in the list. Sized so the sunken well always ends inside the panel's body. */
    private static final int LIST_ROWS = (TradeMenu.LIST_H - 19) / ROW_H;
    private static final int LIST_BODY_Y = 15;
    /** Space a drawn scrollbar takes out of the row width (5px track + 1px gap + 2px breathing). */
    private static final int SCROLLER_GAP = 8;
    private static final int SCROLLER_W = 5;

    private int scroll;
    private boolean draggingScroller;

    public TradeScreen(ResonanceAltarMenu menu, Inventory inventory, Component title) {
        // Both sizes go to super: they are final here, so assigning them afterwards is not an option.
        super(menu, inventory, title, TradeMenu.IMAGE_WIDTH, TradeMenu.IMAGE_HEIGHT);
        this.inventoryLabelY = TradeMenu.INVENTORY_LABEL_Y;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // Coordinates here are already relative to (leftPos, topPos): extractContents pushes
        // translate(leftPos, topPos) before calling this, and extractSlots runs inside the same
        // push. Adding leftPos again drew the whole panel at double the offset -- shoved into the
        // bottom-right corner and running off the screen, with the slot wells twice as far apart
        // as the items vanilla draws in them. So drawing is relative throughout, and the mouse,
        // which arrives in screen coordinates, is offset by hand where it is hit-tested.
        panel(g, 0, 0, this.imageWidth, this.imageHeight);

        // A slot's x/y is where the 16x16 ITEM goes. The well around it is 18x18 and hangs one
        // pixel further out on each side -- vanilla's own slot sprite does the same -- so the well
        // is drawn at -1. Drawn at slot.x instead, every item sat one pixel up and left of the
        // square that was meant to hold it.
        for (Slot slot : menu.slots) {
            slotWell(g, slot.x - 1, slot.y - 1);
        }

        if (!menu.hasWheel()) {
            wheelHint(g, TradeMenu.WHEEL_X - 1, TradeMenu.WHEEL_Y - 1);
            // 26.3's blit takes corners plus normalised UVs; the 1.21.1 form this was written
            // against took u/v, width and height in pixels. Same picture either way: the 32x32 icon
            // into the 16x16 well.
            g.blit(WHEEL_SLOT_ICON, TradeMenu.WHEEL_X, TradeMenu.WHEEL_Y,
                    TradeMenu.WHEEL_X + 16, TradeMenu.WHEEL_Y + 16, 0f, 1f, 0f, 1f);
        }

        renderExperience(g, TradeMenu.SLIDER_X, TradeMenu.SLIDER_Y);
        renderButton(g, TradeMenu.BUTTON_X, TradeMenu.BUTTON_Y, mouseX, mouseY);
        renderList(g, TradeMenu.LIST_X, TradeMenu.LIST_Y,
                TradeMenu.LIST_W, TradeMenu.LIST_H, mouseX, mouseY);

        g.text(this.font, titleFor().getVisualOrderText(), PAD, PAD, TEXT_HEADER, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        // vanilla has already queued the item tooltip; ours goes on top of everything else
        if (within(mouseX, mouseY, this.leftPos + TradeMenu.BUTTON_X, this.topPos + TradeMenu.BUTTON_Y,
                TradeMenu.BUTTON_W, TradeMenu.BUTTON_H) && !canExchange()) {
            Component why = menu.selectedConcept() == null
                    ? Component.translatable("adaptionwheel.gui.select_first")
                    : Component.translatable("adaptionwheel.gui.not_enough_levels");
            g.setTooltipForNextFrame(why, mouseX, mouseY);
        }
    }

    protected Component titleFor() {
        return Component.translatable(menu.titleKey());
    }

    /**
     * What the empty list says, which is one of three different situations.
     *
     * <p>They are ordered by what the player has to do about them: no wheel means the list cannot
     * exist at all, no offering means nothing has been asked, and an empty list with both slots full
     * means the wheel has already learned everything this item opens — which is a <em>result</em>, not
     * a thing to fix, and says so in those words rather than asking for another item.</p>
     */
    protected Component hintFor() {
        if (!menu.hasWheel()) {
            return Component.translatable("adaptionwheel.gui.need_wheel");
        }
        // Two empty lists that want opposite things from the player: a wrong item means put
        // something else in, a wheel that already knows everything this item opens means there is
        // nothing to do here at all. The server answers which one it is, because only it knows
        // whether the item is on the price list or is somebody's drop.
        return menu.isOfferingAccepted()
                ? Component.translatable("adaptionwheel.gui.all_learned")
                : Component.translatable("adaptionwheel.gui.need_item");
    }

    private boolean canExchange() {
        return menu.selectedConcept() != null && menu.canAfford(playerLevel());
    }

    /** The chest GUI's slot well, exact. */
    private void slotWell(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, SLOT_FRAME);
        g.fill(x + 1, y, x + 18, y + 1, SLOT_BODY);
        g.fill(x, y + 17, x + 1, y + 18, SLOT_BODY);
        g.fill(x + 17, y + 1, x + 18, y + 17, SLOT_SHADOW);
        g.fill(x + 1, y + 17, x + 18, y + 18, SLOT_SHADOW);
        g.fill(x + 1, y + 1, x + 17, y + 17, SLOT_BODY);
    }

    /** A slow golden pulse around the empty wheel slot: "put the wheel here". */
    private void wheelHint(GuiGraphicsExtractor g, int x, int y) {
        float pulse = 0.5F + 0.5F * (float) Math.sin(System.currentTimeMillis() / 380.0);
        int alpha = 0x38 + (int) (0x68 * pulse);
        int col = alpha << 24 | 0xFFD84A;
        g.fill(x - 1, y - 1, x + 19, y, col);
        g.fill(x - 1, y + 18, x + 19, y + 19, col);
        g.fill(x - 1, y, x, y + 18, col);
        g.fill(x + 18, y, x + 19, y + 18, col);
        // wheelHint is now handed the well's top-left, so the ring is one pixel further out.
    }

    // ---- experience

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
                g.fill(x + 1, y + 1, x + 1 + filled, y + 2, affordable ? BAR_FULL_HI : BAR_SHORT_HI);
            }
        }
        // quarter ticks
        for (int i = 1; i < 4; i++) {
            int tx = x + 1 + (w - 2) * i / 4;
            g.fill(tx, y + 1, tx + 1, y + BAR_H - 1, 0x55000000);
        }

        g.text(this.font, Component.translatable("adaptionwheel.gui.your_level", playerLevel).getVisualOrderText(),
                x, y + BAR_H + 4, TEXT_DIM, false);
        if (menu.selectedConcept() != null) {
            int py = y + BAR_H + 15;
            orb(g, x, py + 1, affordable);
            // Both halves of the price, because both grow with the level: a player watching items
            // vanish from the slot with only an experience number on screen cannot tell a rising
            // price from a broken block.
            g.text(this.font, Component.translatable("adaptionwheel.gui.price",
                            price, menu.itemCost()).getVisualOrderText(),
                    x + 8, py, affordable ? TEXT : TEXT_SHORT, false);
        }
    }

    /** A 5x5 experience orb, drawn from fills. */
    private static void orb(GuiGraphicsExtractor g, int x, int y, boolean lit) {
        int edge = lit ? 0xFF4FAE1E : 0xFF5A5A5A;
        int core = lit ? 0xFFB6FF3C : 0xFF8A8A8A;
        int spark = lit ? 0xFFF4FFB0 : 0xFFB8B8B8;
        g.fill(x + 1, y, x + 4, y + 5, edge);
        g.fill(x, y + 1, x + 5, y + 4, edge);
        g.fill(x + 1, y + 1, x + 4, y + 4, core);
        g.fill(x + 1, y + 1, x + 2, y + 2, spark);
    }

    private int playerLevel() {
        return Minecraft.getInstance().player == null ? 0 : Minecraft.getInstance().player.experienceLevel;
    }

    // ---- button

    private void renderButton(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
        int w = TradeMenu.BUTTON_W;
        int h = TradeMenu.BUTTON_H;
        boolean on = canExchange();
        boolean hot = on && within(mouseX - this.leftPos, mouseY - this.topPos, x, y, w, h);

        g.fill(x, y, x + w, y + h, hot ? 0xFFFFFFFF : PANEL_EDGE);
        int ix = x + 1, iy = y + 1, iw = w - 2, ih = h - 2;
        g.fill(ix, iy, ix + iw, iy + ih, on ? (hot ? BTN_BODY_HOT : BTN_BODY) : BTN_BODY_OFF);
        int light = on ? BTN_LIGHT : BTN_LIGHT_OFF;
        g.fill(ix, iy, ix + iw, iy + 1, light);
        g.fill(ix, iy, ix + 1, iy + ih, light);
        g.fill(ix, iy + ih - 1, ix + iw, iy + ih, BTN_DARK);
        g.fill(ix + iw - 1, iy, ix + iw, iy + ih, BTN_DARK);

        Component label = Component.translatable("adaptionwheel.gui.exchange");
        int ty = y + (h - 8) / 2;
        if (on) {
            g.centeredText(this.font, label.getString(), x + w / 2, ty, hot ? 0xFFFFFFA0 : 0xFFFFFFFF);
        } else {
            g.text(this.font, label.getString(), x + (w - this.font.width(label)) / 2, ty, 0xFFA0A0A0, false);
        }
    }

    // ---- list

    private void renderList(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY) {
        panel(g, x, y, w, h);
        List<String> pool = menu.candidates();
        int top = y + LIST_BODY_Y;
        int rowsH = LIST_ROWS * ROW_H;
        int maxScroll = Math.max(0, pool.size() - LIST_ROWS);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        int rowX = x + 4;
        int textW = w - 8 - (maxScroll > 0 ? SCROLLER_GAP : 0);

        // header + counter
        g.text(this.font, Component.translatable("adaptionwheel.gui.choose").getVisualOrderText(),
                rowX, y + 5, TEXT_HEADER, false);
        if (!pool.isEmpty()) {
            String count = String.valueOf(pool.size());
            g.text(this.font, count, x + w - 5 - this.font.width(count), y + 5, TEXT_DIM, false);
        }

        // the sunken well
        inset(g, x + 3, top - 1, w - 6, rowsH + 2, LIST_BG);

        if (pool.isEmpty()) {
            List<FormattedCharSequence> lines = this.font.split(hintFor(), w - 20);
            int ty = top + Math.max(2, (rowsH - lines.size() * 9) / 2);
            for (FormattedCharSequence line : lines) {
                g.text(this.font, line, x + (w - this.font.width(line)) / 2, ty, 0xFF9A9A9A, true);
                ty += 9;
            }
            return;
        }

        g.enableScissor(rowX, top, rowX + textW, top + rowsH);
        for (int row = 0; row < LIST_ROWS && row + scroll < pool.size(); row++) {
            int index = row + scroll;
            String concept = pool.get(index);
            int ry = top + row * ROW_H;
            boolean selected = index == menu.selectedIndex();
            boolean hot = within(mouseX - this.leftPos, mouseY - this.topPos, rowX, ry, textW, ROW_H);
            int accent = Concepts.color(concept) & 0xFFFFFF;
            int bright = lighten(accent, 0.35f);

            if ((index & 1) == 1) {
                g.fill(rowX, ry, rowX + textW, ry + ROW_H, ROW_ZEBRA);
            }
            if (selected) {
                g.fill(rowX, ry, rowX + textW, ry + ROW_H, 0x55000000 | accent);
                int edge = 0xFF000000 | bright;
                g.fill(rowX, ry, rowX + textW, ry + 1, edge);
                g.fill(rowX, ry + ROW_H - 1, rowX + textW, ry + ROW_H, edge);
                g.fill(rowX, ry, rowX + 1, ry + ROW_H, edge);
                g.fill(rowX + textW - 1, ry, rowX + textW, ry + ROW_H, edge);
            } else if (hot) {
                g.fill(rowX, ry, rowX + textW, ry + ROW_H, ROW_HOVER);
            }
            // the concept's own colour, always visible
            g.fill(rowX + 2, ry + 2, rowX + 4, ry + ROW_H - 2, 0xFF000000 | bright);

            g.text(this.font, clipped(Concepts.displayName(concept), textW - 9),
                    rowX + 7, ry + 2, selected ? ROW_TEXT_SELECTED : ROW_TEXT, true);
        }
        g.disableScissor();

        if (maxScroll > 0) {
            scroller(g, trackX(), top, rowsH, maxScroll);
        }
    }

    private void scroller(GuiGraphicsExtractor g, int x, int y, int h, int maxScroll) {
        g.fill(x, y, x + SCROLLER_W, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + SCROLLER_W - 1, y + h - 1, 0xFF3A3A42);
        int knobH = knobHeight(h, maxScroll);
        int knobY = y + 1 + (h - 2 - knobH) * scroll / maxScroll;
        int kx = x + 1, kw = SCROLLER_W - 2;
        g.fill(kx, knobY, kx + kw, knobY + knobH, 0xFF8B8B8B);
        g.fill(kx, knobY, kx + kw, knobY + 1, 0xFFE0E0E0);
        g.fill(kx, knobY, kx + 1, knobY + knobH, 0xFFC6C6C6);
        g.fill(kx + kw - 1, knobY, kx + kw, knobY + knobH, 0xFF555555);
        g.fill(kx, knobY + knobH - 1, kx + kw, knobY + knobH, 0xFF555555);
    }

    private int knobHeight(int trackH, int maxScroll) {
        return Math.max(8, trackH * LIST_ROWS / (LIST_ROWS + maxScroll));
    }

    // ---- primitives

    private void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        if (w < 8 || h < 8) {
            return;
        }
        g.fill(x, y, x + w, y + 1, PANEL_EDGE);
        g.fill(x, y + h - 1, x + w, y + h, PANEL_EDGE);
        g.fill(x, y, x + 1, y + h, PANEL_EDGE);
        g.fill(x + w - 1, y, x + w, y + h, PANEL_EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + 3, PANEL_HILIGHT);
        g.fill(x + 1, y + 1, x + 3, y + h - 1, PANEL_HILIGHT);
        // bottom-right shades, like the real container: a darker edge, not a second highlight
        g.fill(x + 1, y + h - 3, x + w - 1, y + h - 1, 0xFF555555);
        g.fill(x + w - 3, y + 1, x + w - 1, y + h - 1, 0xFF555555);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, PANEL_BODY);
        // soften the corners the way the vanilla texture does
        g.fill(x + w - 3, y + 1, x + w - 1, y + 3, PANEL_BODY);
        g.fill(x + 1, y + h - 3, x + 3, y + h - 1, PANEL_BODY);
    }

    /** A sunken well: dark top-left, light bottom-right. */
    private void inset(GuiGraphicsExtractor g, int x, int y, int w, int h, int body) {
        g.fill(x, y, x + w, y + h, SLOT_FRAME);
        g.fill(x + w - 1, y, x + w, y + h, SLOT_SHADOW);
        g.fill(x, y + h - 1, x + w, y + h, SLOT_SHADOW);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, body);
    }

    /** {@code rgb} moved {@code amount} of the way to white. */
    private static int lighten(int rgb, float amount) {
        int r = (int) ((rgb >> 16 & 0xFF) + (255 - (rgb >> 16 & 0xFF)) * amount);
        int gg = (int) ((rgb >> 8 & 0xFF) + (255 - (rgb >> 8 & 0xFF)) * amount);
        int b = (int) ((rgb & 0xFF) + (255 - (rgb & 0xFF)) * amount);
        return r << 16 | gg << 8 | b;
    }

    private String clipped(Component text, int maxWidth) {
        String plain = text.getString();
        if (this.font.width(plain) <= maxWidth) {
            return plain;
        }
        return this.font.plainSubstrByWidth(plain, Math.max(0, maxWidth - this.font.width("..."))) + "...";
    }

    // ------------------------------------------------------------------ input

    /** Relative to (leftPos, topPos), like everything drawn in {@link #extractLabels}. */
    private int trackX() {
        return TradeMenu.LIST_X + TradeMenu.LIST_W - 10;
    }

    /** Relative to (leftPos, topPos), like everything drawn in {@link #extractLabels}. */
    private int listTop() {
        return TradeMenu.LIST_Y + LIST_BODY_Y;
    }

    private void click() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void dragScrollerTo(double screenY) {
        int maxScroll = maxScroll();
        if (maxScroll <= 0) {
            return;
        }
        int h = LIST_ROWS * ROW_H;
        int knobH = knobHeight(h, maxScroll);
        double my = screenY - this.topPos;
        double ratio = (my - listTop() - knobH / 2.0) / Math.max(1, h - 2 - knobH);
        scroll = (int) Math.round(Math.max(0, Math.min(1, ratio)) * maxScroll);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x() - this.leftPos;
        double my = event.y() - this.topPos;
        int x = 0;
        int y = 0;
        // Not the literal 0: 26.3 numbers the left button 1. See MouseButtons. Anything else falls
        // straight through to the slot handling below, which is what a right-click should do.
        if (!MouseButtons.isLeft(event)) {
            return super.mouseClicked(event, doubleClick);
        }
        if (within(mx, my, x + TradeMenu.BUTTON_X, y + TradeMenu.BUTTON_Y,
                TradeMenu.BUTTON_W, TradeMenu.BUTTON_H)) {
            if (canExchange()) {
                click();
                TradeActionPayload.exchange();
            }
            return true;
        }
        int maxScroll = maxScroll();
        if (maxScroll > 0 && within(mx, my, trackX(), listTop(), SCROLLER_W, LIST_ROWS * ROW_H)) {
            draggingScroller = true;
            dragScrollerTo(my);
            return true;
        }
        int rowsW = TradeMenu.LIST_W - 8 - (maxScroll > 0 ? SCROLLER_GAP : 0);
        if (within(mx, my, x + TradeMenu.LIST_X + 4, listTop(), rowsW, LIST_ROWS * ROW_H)) {
            int row = (int) ((my - listTop()) / ROW_H) + scroll;
            if (row < menu.candidates().size()) {
                click();
                TradeActionPayload.select(row);
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingScroller) {
            dragScrollerTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingScroller = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (mx >= this.leftPos + TradeMenu.LIST_X
                && mx < this.leftPos + TradeMenu.LIST_X + TradeMenu.LIST_W) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    private int maxScroll() {
        return Math.max(0, menu.candidates().size() - LIST_ROWS);
    }

    private static boolean within(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
