package ru.adaptionwheel.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import ru.adaptionwheel.adapt.AdaptationDomain;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.category.Synergies;
import ru.adaptionwheel.data.AdaptionTask;
import ru.adaptionwheel.data.PlayerAdaption;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AdaptationScreen extends Screen {

    private static final int PANEL_EDGE = 0xFF000000;
    private static final int PANEL_HILIGHT = 0xFFFFFFFF;
    private static final int PANEL_SHADOW = 0xFF555555;
    private static final int PANEL_BODY = 0xFFC6C6C6;
    private static final int PANEL_BODY_HOVER = 0xFFD8D8D8;

    private static final int WELL_BODY = 0xFF8B8B8B;
    private static final int WELL_SHADOW = 0xFF373737;
    private static final int CARD_BODY = 0xFFC6C6C6;
    private static final int CARD_HOVER = 0xFFDADADA;
    private static final int CARD_LIGHT = 0xFFFFFFFF;
    private static final int CARD_DARK = 0xFF6B6B6B;

    private static final int BAR_FRAME = 0xFF09100C;
    private static final int BAR_EMPTY = 0xFF28332D;
    private static final int BAR_FULL = 0xFF71A549;
    private static final int BAR_GOLD = 0xFFE3B02B;

    private static final int CHIP_FRAME = 0xFF3F6B26;
    private static final int CHIP_BODY = 0xFFBCD6A3;
    private static final int CHIP_TEXT = 0xFF244312;

    private static final int TEXT = 0xFF404040;
    private static final int TEXT_DIM = 0xFF6A6A6A;
    private static final int TEXT_HEADER = 0xFF3F3F3F;
    private static final int TEXT_WHITE = 0xFFFFFFFF;

    private static final int BAR_H = 5;
    private static final int SCROLLER_W = 10;
    private static final int SCROLLER_H = 18;
    private static final int TAB_H = 14;
    private static final int ENTRY_H = 44;
    private static final int CARD_H = ENTRY_H - 2;
    private static final int PAD = 8;
    private static final int MIN_W = 200;
    private static final int MAX_TICKS = 12;
    private static final long OPEN_NANOS = 160_000_000L;

    private static final AdaptationDomain ALL_TAB = null;

    private record Chip(String text, int x, int line, int w) {
    }

    private final List<AdaptationDomain> tabs = new ArrayList<>();

    private final List<int[]> tabHitboxes = new ArrayList<>();
    private final List<String> visibleEntries = new ArrayList<>();
    private final Map<String, List<FormattedCharSequence>> descriptionCache = new HashMap<>();
    private final List<Chip> chips = new ArrayList<>();

    private AdaptationDomain selectedTab = ALL_TAB;

    private List<String> lastConcepts = List.of();

    private int panelX;
    private int basePanelY;
    private int panelY;
    private int panelW;
    private int panelH;
    private int tabsY;
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private int chipLines;

    private int synergyStripH;

    private double scrollOffset;
    private double scrollTarget;
    private boolean draggingThumb;
    private double dragGrab;

    private long openedAt;
    private long lastFrame;

    public AdaptationScreen() {
        super(Component.translatable("adaptionwheel.gui.title"));
    }

    @Override
    protected void init() {
        panelW = Math.max(MIN_W, Math.min(360, this.width - 20));
        panelX = (this.width - panelW) / 2;
        panelH = Math.max(130, Math.min(270, this.height - 40));
        basePanelY = Math.max(8, this.height / 2 - panelH / 2);
        panelY = basePanelY;
        openedAt = System.nanoTime();
        lastFrame = openedAt;
        layoutList();
        lastConcepts = List.of();
        refreshIfStale();
    }

    private void layoutList() {
        int sepY = headerSeparatorY();
        tabsY = sepY + 5 + synergyStripH + 2;
        int footerY = footerY();
        listX = panelX + PAD + 2;
        listY = tabsY + TAB_H + 5;
        listW = panelW - PAD * 2 - 4 - SCROLLER_W - 3;
        listH = Math.max(0, footerY - 5 - listY);
    }

    private int headerSeparatorY() {
        return panelY + PAD + this.font.lineHeight + 3;
    }

    private int footerY() {
        return panelY + panelH - PAD - this.font.lineHeight + 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void refreshIfStale() {
        List<String> current = collectConcepts();
        current.sort(Comparator.naturalOrder());
        if (current.equals(lastConcepts)) {
            return;
        }
        lastConcepts = current;
        rebuildTabs();
        refreshEntries();
    }

    private void rebuildTabs() {
        tabs.clear();
        Set<AdaptationDomain> present = new HashSet<>();
        collectConcepts().forEach(c -> present.add(AdaptationRegistrySafe.domainOf(c)));
        tabs.addAll(present.stream().sorted(Comparator.comparingInt(Enum::ordinal)).toList());

        if (selectedTab != null && !tabs.contains(selectedTab)) {
            selectedTab = ALL_TAB;
        }
    }

    private List<String> collectConcepts() {
        Set<String> concepts = new HashSet<>();
        concepts.addAll(ClientAdaption.LEVELS.keySet());
        concepts.addAll(ClientAdaption.ADAPTED);
        for (AdaptionTask task : ClientAdaption.TASKS) {
            concepts.add(task.concept);
        }
        return new ArrayList<>(concepts);
    }

    private void refreshEntries() {
        visibleEntries.clear();
        descriptionCache.clear();
        for (String concept : collectConcepts()) {
            if (selectedTab != null && AdaptationRegistrySafe.domainOf(concept) != selectedTab) {
                continue;
            }
            visibleEntries.add(concept);
        }
        visibleEntries.sort(Comparator
                .comparingInt((String c) -> AdaptationRegistrySafe.domainOf(c).ordinal())
                .thenComparing(c -> Concepts.displayName(c).getString(), String.CASE_INSENSITIVE_ORDER));
        clampScroll();
    }

    private double maxScroll() {
        return Math.max(0, visibleEntries.size() * ENTRY_H - 2 - listH);
    }

    private void clampScroll() {
        double max = maxScroll();
        scrollTarget = Math.max(0, Math.min(max, scrollTarget));
        scrollOffset = Math.max(0, Math.min(max, scrollOffset));
    }

    private boolean overList(double mx, double my) {
        return mx >= listX && mx < listX + listW + SCROLLER_W + 3
                && my >= listY && my < listY + listH;
    }

    private int trackX() {
        return listX + listW + 3;
    }

    private int thumbY() {
        double max = maxScroll();
        int travel = listH - SCROLLER_H;
        return max <= 0 ? listY : listY + (int) Math.round(travel * (scrollOffset / max));
    }

    private boolean scrollable() {
        return maxScroll() > 0 && listH > SCROLLER_H;
    }

    private void dragTo(double mouseY) {
        int travel = listH - SCROLLER_H;
        double t = (mouseY - dragGrab - listY) / travel;
        scrollTarget = scrollOffset = Math.max(0, Math.min(1, t)) * maxScroll();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (overList(mouseX, mouseY)) {
            scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget - scrollY * ENTRY_H));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        double mouseX = mx;
        double mouseY = my;

        if (button == 0) {
            for (int i = 0; i < tabHitboxes.size(); i++) {
                int[] b = tabHitboxes.get(i);
                if (mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3]) {
                    selectedTab = i == 0 ? ALL_TAB : tabs.get(i - 1);
                    scrollTarget = scrollOffset = 0;
                    refreshEntries();
                    return true;
                }
            }
            if (scrollable() && mouseX >= trackX() && mouseX < trackX() + SCROLLER_W
                    && mouseY >= listY && mouseY < listY + listH) {
                int ty = thumbY();

                dragGrab = (mouseY >= ty && mouseY < ty + SCROLLER_H) ? mouseY - ty : SCROLLER_H / 2.0;
                draggingThumb = true;
                dragTo(mouseY);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingThumb) {
            dragTo(my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (draggingThumb) {
            draggingThumb = false;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(null);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {

        renderBackground(g, mouseX, mouseY, partialTick);
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - lastFrame) / 1.0e9);
        lastFrame = now;

        float open = Math.min(1f, (now - openedAt) / (float) OPEN_NANOS);
        float eased = 1f - (1f - open) * (1f - open) * (1f - open);
        panelY = basePanelY + Math.round((1f - eased) * 8f);

        refreshIfStale();
        layoutSynergies();

        layoutList();
        animateScroll(dt);

        g.fill(0, 0, this.width, this.height, ((int) (0xC0 * eased) << 24) | 0x101010);

        g.fill(panelX + 4, panelY + 4, panelX + panelW + 4, panelY + panelH + 4, 0x30000000);
        g.fill(panelX + 2, panelY + 2, panelX + panelW + 2, panelY + panelH + 2, 0x30000000);

        panel(g, panelX, panelY, panelW, panelH, PANEL_BODY);
        renderHeader(g);
        renderSynergyChips(g);
        renderTabs(g, mouseX, mouseY);
        renderList(g, mouseX, mouseY, now);
        renderScrollbar(g, mouseX, mouseY);
        renderFooter(g);
    }

    private void animateScroll(double dt) {
        clampScroll();
        if (draggingThumb) {
            return;
        }
        double diff = scrollTarget - scrollOffset;
        if (Math.abs(diff) < 0.4) {
            scrollOffset = scrollTarget;
        } else {

            scrollOffset += diff * (1.0 - Math.exp(-dt * 20.0));
        }
    }

    private void renderHeader(GuiGraphics g) {
        g.drawString(this.font, this.title, panelX + PAD, panelY + PAD, TEXT_HEADER, false);

        String count = Component.translatable("adaptionwheel.gui.count", ClientAdaption.adaptedCount).getString();
        int cw = this.font.width(count) + 8;
        int cx = panelX + panelW - PAD - cw;
        inset(g, cx, panelY + PAD - 3, cw, this.font.lineHeight + 5, WELL_BODY);
        g.drawString(this.font, count, cx + 4, panelY + PAD, TEXT_WHITE, true);

        int sepY = headerSeparatorY();
        g.fill(panelX + PAD - 1, sepY, panelX + panelW - PAD + 1, sepY + 1, PANEL_SHADOW);
        g.fill(panelX + PAD - 1, sepY + 1, panelX + panelW - PAD + 1, sepY + 2, PANEL_HILIGHT);
    }

    private void layoutSynergies() {
        chips.clear();
        int avail = panelW - PAD * 2;
        int x = 0;
        int line = 0;
        for (Synergies.Synergy synergy : Synergies.ALL) {
            if (!Synergies.satisfied(ClientAdaption.LEVELS, ClientAdaption.ADAPTED, synergy)) {
                continue;
            }
            String name = Component.translatable("adaptionwheel.synergy." + synergy.id()).getString();
            int w = this.font.width(name) + 8;
            if (x > 0 && x + w > avail) {
                line++;
                x = 0;
            }
            chips.add(new Chip(name, x, line, w));
            x += w + 3;
        }
        chipLines = chips.isEmpty() ? 0 : line + 1;
        synergyStripH = chipLines * (this.font.lineHeight + 6);
    }

    private void renderSynergyChips(GuiGraphics g) {
        int baseY = headerSeparatorY() + 5;
        int h = this.font.lineHeight + 4;
        for (Chip c : chips) {
            int x = panelX + PAD + c.x();
            int y = baseY + c.line() * (this.font.lineHeight + 6);
            g.fill(x + 1, y, x + c.w() - 1, y + h, CHIP_FRAME);
            g.fill(x, y + 1, x + c.w(), y + h - 1, CHIP_FRAME);
            g.fill(x + 1, y + 1, x + c.w() - 1, y + h - 1, CHIP_BODY);
            g.fill(x + 1, y + 1, x + c.w() - 1, y + 2, 0x60FFFFFF);
            g.drawString(this.font, c.text(), x + 4, y + 2, CHIP_TEXT, false);
        }
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        tabHitboxes.clear();
        int y = tabsY;
        int x = panelX + PAD - 1;

        List<Component> labels = new ArrayList<>();
        labels.add(Component.translatable("adaptionwheel.gui.tab_all"));
        for (AdaptationDomain domain : tabs) {
            labels.add(domain.translation());
        }
        for (int i = 0; i < labels.size(); i++) {
            boolean selected = i == 0 ? selectedTab == ALL_TAB : tabs.get(i - 1) == selectedTab;
            String label = labels.get(i).getString();
            int w = this.font.width(label) + PAD * 2 - 2;
            boolean hovered = !selected && mouseX >= x && mouseX < x + w
                    && mouseY >= y && mouseY < y + TAB_H;

            int ty = selected ? y - 2 : y;
            int th = TAB_H + (selected ? 2 : 0);
            panel(g, x, ty, w, th, hovered ? PANEL_BODY_HOVER : PANEL_BODY);
            g.drawString(this.font, label, x + (w - this.font.width(label)) / 2, ty + 3,
                    selected ? TEXT_HEADER : (hovered ? TEXT : TEXT_DIM), false);
            tabHitboxes.add(new int[]{x, ty, w, th});
            x += w + 1;
        }
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY, long now) {

        int wellX = panelX + PAD;
        int wellW = panelW - PAD * 2;
        inset(g, wellX, listY - 2, wellW, listH + 4, WELL_BODY);

        if (visibleEntries.isEmpty()) {
            Component empty = Component.translatableWithFallback("adaptionwheel.gui.empty", "No adaptations yet");
            int tw = this.font.width(empty);
            g.drawString(this.font, empty, wellX + (wellW - tw) / 2, listY + listH / 2 - 4, TEXT_WHITE, true);
            return;
        }

        g.enableScissor(listX, listY, listX + listW, listY + listH);
        int offset = (int) Math.round(scrollOffset);
        int y = listY - offset;
        for (String concept : visibleEntries) {
            if (y + ENTRY_H >= listY && y <= listY + listH) {
                boolean hovered = mouseX >= listX && mouseX < listX + listW
                        && mouseY >= Math.max(y, listY) && mouseY < Math.min(y + CARD_H, listY + listH)
                        && !draggingThumb;
                renderEntry(g, concept, y, hovered, now);
            }
            y += ENTRY_H;
        }
        g.disableScissor();
    }

    private void renderEntry(GuiGraphics g, String concept, int y, boolean hovered, long now) {
        int x = listX;
        int w = listW;
        int accent = onPanel(Concepts.color(concept));

        g.fill(x, y, x + w, y + CARD_H, CARD_DARK);
        g.fill(x, y, x + w - 1, y + CARD_H - 1, CARD_LIGHT);
        g.fill(x + 1, y + 1, x + w - 1, y + CARD_H - 1, hovered ? CARD_HOVER : CARD_BODY);

        g.fill(x + 1, y + 1, x + 4, y + CARD_H - 1, accent);
        g.fill(x + 1, y + 1, x + 2, y + CARD_H - 1, mix(accent, 0xFFFFFFFF, 0.35f));

        int contentX = x + 9;
        int contentW = w - 9 - 6;

        int level = ClientAdaption.LEVELS.getOrDefault(concept, 0);
        boolean adapted = ClientAdaption.isAdapted(concept);
        AdaptionTask runningTask = null;
        for (AdaptionTask task : ClientAdaption.TASKS) {
            if (task.concept.equals(concept)) {
                runningTask = task;
                break;
            }
        }
        boolean leveled = Concepts.isLevelBased(concept);

        String state;
        float barFill;
        int stateColor;
        int barColor = BAR_FULL;
        boolean shimmer = false;
        int ticks = 0;
        if (runningTask != null) {
            float p = ClientAdaption.taskProgress(runningTask);
            state = Component.translatable("adaptionwheel.gui.analyzing").getString()
                    + " " + (int) (p * 100) + "%";
            barFill = p;
            stateColor = TEXT;
            shimmer = true;
        } else if (!leveled && adapted) {
            state = Component.translatable("adaptionwheel.gui.adapted").getString();
            barFill = 1f;
            stateColor = CHIP_FRAME;
        } else if (leveled && level >= PlayerAdaption.MAX_LEVEL) {
            state = Component.translatable("adaptionwheel.gui.max_reached").getString();
            barFill = 1f;
            barColor = BAR_GOLD;
            stateColor = 0xFF9A6A00;
            ticks = PlayerAdaption.MAX_LEVEL <= MAX_TICKS ? PlayerAdaption.MAX_LEVEL : 0;
        } else if (leveled) {
            state = Component.translatable("adaptionwheel.gui.level", level,
                    PlayerAdaption.MAX_LEVEL).getString();
            barFill = level / (float) PlayerAdaption.MAX_LEVEL;
            stateColor = TEXT;
            ticks = PlayerAdaption.MAX_LEVEL <= MAX_TICKS ? PlayerAdaption.MAX_LEVEL : 0;
        } else {
            state = Component.translatable("adaptionwheel.gui.pending").getString();
            barFill = 0f;
            stateColor = TEXT_DIM;
        }

        int stateW = this.font.width(state);
        g.drawString(this.font, state, contentX + contentW - stateW, y + 5, stateColor, false);

        String name = Concepts.displayName(concept).getString();
        g.drawString(this.font, this.font.plainSubstrByWidth(name, contentW - stateW - 8),
                contentX, y + 5, accent, false);

        bar(g, contentX, y + 16, contentW, barFill, barColor, ticks, shimmer, now);

        List<FormattedCharSequence> desc = descriptionCache.computeIfAbsent(concept, k -> {
            Component text = Component.translatableWithFallback("adaptionwheel.desc." + k, "");
            return text.getString().isEmpty() ? List.of() : this.font.split(text, contentW);
        });
        for (int i = 0; i < desc.size() && i < 2; i++) {
            g.drawString(this.font, desc.get(i), contentX, y + 24 + i * 9, TEXT_DIM, false);
        }
    }

    private void bar(GuiGraphics g, int x, int y, int width, float fill,
                     int color, int ticks, boolean shimmer, long now) {
        g.fill(x, y, x + width, y + BAR_H, BAR_FRAME);
        g.fill(x + 1, y + 1, x + width - 1, y + BAR_H - 1, BAR_EMPTY);

        int inner = width - 2;
        int fillW = Math.round(inner * Math.max(0f, Math.min(1f, fill)));
        if (fillW > 0) {
            int x0 = x + 1;
            g.fill(x0, y + 1, x0 + fillW, y + BAR_H - 1, color);
            g.fill(x0, y + 1, x0 + fillW, y + 2, mix(color, 0xFFFFFFFF, 0.38f));
            g.fill(x0, y + BAR_H - 2, x0 + fillW, y + BAR_H - 1, mix(color, 0xFF000000, 0.25f));

            if (shimmer) {
                int bandW = 10;
                int span = fillW + bandW * 2;
                int pos = (int) ((now / 12_000_000L) % span) - bandW;
                int a = Math.max(x0, x0 + pos);
                int b = Math.min(x0 + fillW, x0 + pos + bandW);
                if (b > a) {
                    g.fill(a, y + 1, b, y + BAR_H - 1, 0x45FFFFFF);
                }
            }
        }
        for (int i = 1; i < ticks; i++) {
            int tx = x + 1 + Math.round(inner * (i / (float) ticks));
            g.fill(tx, y + 1, tx + 1, y + BAR_H - 1, BAR_FRAME);
        }
    }

    private void renderScrollbar(GuiGraphics g, int mouseX, int mouseY) {
        if (!scrollable()) {
            return;
        }
        int trackX = trackX();

        g.fill(trackX, listY, trackX + SCROLLER_W, listY + listH, WELL_SHADOW);
        g.fill(trackX + 1, listY + 1, trackX + SCROLLER_W, listY + listH, 0xFF2A2A2A);

        int ty = thumbY();
        boolean hot = draggingThumb || (mouseX >= trackX && mouseX < trackX + SCROLLER_W
                && mouseY >= ty && mouseY < ty + SCROLLER_H);
        panel(g, trackX, ty, SCROLLER_W, SCROLLER_H, hot ? PANEL_BODY_HOVER : PANEL_BODY, true);

        int gx = trackX + 3;
        for (int i = 0; i < 3; i++) {
            int gy = ty + SCROLLER_H / 2 - 3 + i * 3;
            g.fill(gx, gy, gx + SCROLLER_W - 6, gy + 1, PANEL_SHADOW);
        }
    }

    private void renderFooter(GuiGraphics g) {
        int y = footerY();
        g.drawString(this.font, Component.translatable("adaptionwheel.gui.footer_scroll"),
                panelX + PAD, y, TEXT_DIM, false);
        if (!visibleEntries.isEmpty()) {
            String shown = visibleEntries.size() + "";
            g.drawString(this.font, shown, panelX + panelW - PAD - this.font.width(shown), y, TEXT_DIM, false);
        }
    }

    private void panel(GuiGraphics g, int x, int y, int w, int h, int body) {
        panel(g, x, y, w, h, body, false);
    }

    private void panel(GuiGraphics g, int x, int y, int w, int h, int body, boolean thin) {
        if (w < 6 || h < 6) {
            return;
        }
        int b = thin ? 1 : 2;

        g.fill(x + 1, y, x + w - 1, y + 1, PANEL_EDGE);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, PANEL_EDGE);
        g.fill(x, y + 1, x + 1, y + h - 1, PANEL_EDGE);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, PANEL_EDGE);

        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, body);

        g.fill(x + 1, y + h - 1 - b, x + w - 1, y + h - 1, PANEL_SHADOW);
        g.fill(x + w - 1 - b, y + 1, x + w - 1, y + h - 1, PANEL_SHADOW);
        g.fill(x + 1, y + 1, x + w - 1 - b, y + 1 + b, PANEL_HILIGHT);
        g.fill(x + 1, y + 1, x + 1 + b, y + h - 1 - b, PANEL_HILIGHT);
    }

    private void inset(GuiGraphics g, int x, int y, int w, int h, int body) {
        g.fill(x, y, x + w, y + h, PANEL_HILIGHT);
        g.fill(x, y, x + w - 1, y + h - 1, WELL_SHADOW);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, body);
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int gr = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (gr << 8) | bl;
    }

    private static int onPanel(int rgb) {
        float f = 0.52f;
        int r = Math.round(((rgb >> 16) & 0xFF) * f);
        int g = Math.round(((rgb >> 8) & 0xFF) * f);
        int b = Math.round((rgb & 0xFF) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static final class AdaptationRegistrySafe {
        static ru.adaptionwheel.adapt.AdaptationDomain domainOf(String concept) {
            return ru.adaptionwheel.adapt.AdaptationRegistry.domainOf(concept);
        }
    }
}
