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
import ru.adaptionwheel.network.ToggleAdaptationPayload;

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
    private static final int TOGGLE_W = 18;
    private static final int TOGGLE_H = 9;
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

    /** Search text, matched against display names, concept keys and descriptions. */
    private String search = "";
    private boolean searchFocused;
    /** Caret position inside {@link #search}, as a character offset. */
    private int searchCaret;
    /** Anchor for shift-click selection; null when there is none. */
    private String selectionAnchor;

    private static final int SEARCH_H = 9;
    private static final int SEARCH_W = 132;
    /** Room reserved to the right of the field for the match counter. */
    private static final int MATCH_W = 34;

    private int panelX;
    private int basePanelY;
    private int panelY;
    private int panelW;
    private int panelH;
    private int tabsY;
    private int tabX;
    private int tabW;
    private int tabViewportH;
    private double tabScroll;
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
    private int lastMouseX;
    private int lastMouseY;

    public AdaptationScreen() {
        super(Component.translatable("adaptionwheel.gui.title"));
    }

    @Override
    protected void init() {
        panelW = Math.max(MIN_W, Math.min(360, this.width - 20));
        panelX = (this.width - panelW) / 2;
        panelH = Math.max(200, Math.min(340, this.height - 40));
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
        tabX = panelX + PAD - 1;
        tabW = tabColumnWidth();
        listX = tabX + tabW + 5;
        listY = tabsY;
        listW = panelX + panelW - PAD - 1 - listX - SCROLLER_W - 3;
        listH = Math.max(0, footerY - 5 - listY);
        tabViewportH = listH;
    }

    private int tabColumnWidth() {
        int widest = this.font.width(Component.translatable("adaptionwheel.gui.tab_all").getString());
        for (AdaptationDomain domain : AdaptationDomain.values()) {
            widest = Math.max(widest, this.font.width(domain.translation().getString()));
        }
        return Math.min(88, widest + PAD * 2 - 2);
    }

    private double maxTabScroll() {
        return Math.max(0, tabLabels().size() * (TAB_H + 1) - tabViewportH);
    }

    private int headerSeparatorY() {
        return panelY + PAD + this.font.lineHeight + 3;
    }

    private int footerY() {
        return panelY + panelH - PAD - this.font.lineHeight + 2;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchFocused) {
            boolean control = (modifiers & GLFW_MOD_CONTROL) != 0;
            switch (keyCode) {
                case GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER -> {
                    searchFocused = false;
                    return true;
                }
                case GLFW_KEY_ESCAPE -> {
                    // Escape clears a non-empty search first, and only then gives up the field,
                    // so one key does the obvious thing at each step instead of always closing.
                    if (!search.isEmpty()) {
                        search = "";
                        searchCaret = 0;
                        refreshEntries();
                    } else {
                        searchFocused = false;
                    }
                    return true;
                }
                case GLFW_KEY_BACKSPACE -> {
                    if (searchCaret > 0) {
                        search = search.substring(0, searchCaret - 1) + search.substring(searchCaret);
                        searchCaret--;
                        refreshEntries();
                    }
                    return true;
                }
                case GLFW_KEY_DELETE -> {
                    if (searchCaret < search.length()) {
                        search = search.substring(0, searchCaret) + search.substring(searchCaret + 1);
                        refreshEntries();
                    }
                    return true;
                }
                case GLFW_KEY_LEFT -> {
                    searchCaret = Math.max(0, searchCaret - 1);
                    return true;
                }
                case GLFW_KEY_RIGHT -> {
                    searchCaret = Math.min(search.length(), searchCaret + 1);
                    return true;
                }
                case GLFW_KEY_HOME -> {
                    searchCaret = 0;
                    return true;
                }
                case GLFW_KEY_END -> {
                    searchCaret = search.length();
                    return true;
                }
                case GLFW_KEY_V -> {
                    if (control) {
                        String clip = net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard();
                        if (clip != null && !clip.isEmpty()) {
                            search = search.substring(0, searchCaret) + clip + search.substring(searchCaret);
                            searchCaret += clip.length();
                            refreshEntries();
                        }
                        return true;
                    }
                    break;
                }
                case GLFW_KEY_A -> {
                    if (control) {
                        search = "";
                        searchCaret = 0;
                        refreshEntries();
                        return true;
                    }
                    break;
                }
                default -> { }
            }
            // Any other key while the field has focus belongs to the field, so the screen does not
            // also act on it. Without this the number keys and the movement keys leak through.
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchFocused && !Character.isISOControl(codePoint)) {
            search = search.substring(0, searchCaret) + codePoint + search.substring(searchCaret);
            searchCaret++;
            refreshEntries();
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private static final int GLFW_MOD_CONTROL = org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL;
    private static final int GLFW_KEY_ENTER = org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
    private static final int GLFW_KEY_KP_ENTER = org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER;
    private static final int GLFW_KEY_ESCAPE = org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
    private static final int GLFW_KEY_BACKSPACE = org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE;
    private static final int GLFW_KEY_DELETE = org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE;
    private static final int GLFW_KEY_LEFT = org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT;
    private static final int GLFW_KEY_RIGHT = org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
    private static final int GLFW_KEY_HOME = org.lwjgl.glfw.GLFW.GLFW_KEY_HOME;
    private static final int GLFW_KEY_END = org.lwjgl.glfw.GLFW.GLFW_KEY_END;
    private static final int GLFW_KEY_A = org.lwjgl.glfw.GLFW.GLFW_KEY_A;
    private static final int GLFW_KEY_V = org.lwjgl.glfw.GLFW.GLFW_KEY_V;

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
        String needle = search.strip().toLowerCase(java.util.Locale.ROOT);
        for (String concept : collectConcepts()) {
            if (selectedTab != null && AdaptationRegistrySafe.domainOf(concept) != selectedTab) {
                continue;
            }
            if (!needle.isEmpty() && !matches(concept, needle)) {
                continue;
            }
            visibleEntries.add(concept);
        }
        visibleEntries.sort(Comparator
                .comparingInt((String c) -> AdaptationRegistrySafe.domainOf(c).ordinal())
                .thenComparing(c -> Concepts.displayName(c).getString(), String.CASE_INSENSITIVE_ORDER));
        clampScroll();
    }

    /**
     * Matches the display name, the concept key and the description.
     *
     * <p>All three, because the player does not know which of them they are looking for: "drown"
     * is in the name and the key, but a description like "you cannot suffocate" is not. Matching
     * every whitespace-separated word rather than the whole string means "fall boss" finds things
     * that mention both, in either order.
     */
    private boolean matches(String concept, String needle) {
        StringBuilder haystack = new StringBuilder();
        haystack.append(Concepts.displayName(concept).getString()).append(' ');
        haystack.append(concept).append(' ');
        haystack.append(conceptDescription(concept));
        String text = haystack.toString().toLowerCase(java.util.Locale.ROOT);
        for (String word : needle.split("\\s+")) {
            if (!word.isEmpty() && !text.contains(word)) {
                return false;
            }
        }
        return true;
    }

    private String conceptDescription(String concept) {
        List<FormattedCharSequence> lines = descriptionCache.computeIfAbsent(concept,
                k -> {
                    Component text = Component.translatableWithFallback("adaptionwheel.desc." + k, "");
                    return text.getString().isEmpty() ? List.<FormattedCharSequence>of()
                            : this.font.split(text, Math.max(40, panelW - 46));
                });
        StringBuilder sb = new StringBuilder();
        for (FormattedCharSequence line : lines) {
            sb.append(line).append(' ');
        }
        return sb.toString();
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
        if (mouseX >= tabX && mouseX < tabX + tabW && mouseY >= tabsY && mouseY < tabsY + tabViewportH) {
            tabScroll = Math.max(0, Math.min(maxTabScroll(), tabScroll - scrollY * (TAB_H + 1)));
            return true;
        }
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

        // The search field takes the click before any row, so typing into it never toggles an
        // adaptation underneath. Returning true unconditionally on failure is what the container
        // screens do, and it is what made a dead click look like the game ate it.
        int[] box = searchHitbox();
        if (mouseX >= box[0] && mouseX < box[0] + box[2]
                && mouseY >= box[1] && mouseY < box[1] + box[3]) {
            if (button == 0) {
                searchFocused = true;
                searchCaret = caretAt(search, mouseX - box[0] - 2);
                return true;
            }
        } else if (button == 0) {
            searchFocused = false;
        }

        if (button == 0) {
            for (String concept : visibleEntries) {
                int[] hit = toggleHitbox(concept);
                if (hit == null) {
                    continue;
                }
                if (mouseX >= hit[0] && mouseX < hit[0] + hit[2] && mouseY >= hit[1] && mouseY < hit[1] + hit[3]) {
                    ToggleAdaptationPayload.send(concept);
                    return true;
                }
            }
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
        lastMouseX = mouseX;
        lastMouseY = mouseY;
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
        renderSearch(g, mouseX, mouseY);
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

    private List<Component> tabLabels() {
        List<Component> labels = new ArrayList<>();
        labels.add(Component.translatable("adaptionwheel.gui.tab_all"));
        for (AdaptationDomain domain : tabs) {
            labels.add(domain.translation());
        }
        return labels;
    }

    private void renderSearch(GuiGraphics g, int mouseX, int mouseY) {
        int[] box = searchHitbox();
        int x = box[0];
        int y = box[1];
        boolean hovered = mouseX >= x && mouseX < x + SEARCH_W && mouseY >= y && mouseY < y + SEARCH_H;

        // A sunken well, so it reads as an input rather than a label.
        g.fill(x - 1, y - 1, x + SEARCH_W + 1, y + SEARCH_H + 1, 0xFF373737);
        g.fill(x, y, x + SEARCH_W, y + SEARCH_H, hovered ? 0xFFA8A8A8 : 0xFF8B8B8B);

        String shown = search.isEmpty() && !searchFocused ? "" : search;
        g.enableScissor(x, y, x + SEARCH_W, y + SEARCH_H);
        String visible = shown.length() > 26 ? shown.substring(0, 26) : shown;
        int textColour = search.isEmpty() ? 0xFF6B6B6B : 0x1A1A1A;
        if (search.isEmpty()) {
            g.drawString(this.font, Component.translatable("adaptionwheel.gui.search_hint"),
                    x + 2, y + 1, textColour, false);
        } else {
            g.drawString(this.font, visible, x + 2, y + 1, 0x1A1A1A, false);
        }
        if (searchFocused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            int cx = x + 2 + this.font.width(visible.substring(0, Math.min(searchCaret, visible.length())));
            g.fill(cx, y + 1, cx + 1, y + SEARCH_H - 1, 0x1A1A1A);
        }
        g.disableScissor();

        int hits = search.strip().isEmpty() ? 0 : countMatches();
        if (hits > 0) {
            g.drawString(this.font, hits + " / " + allInTab(),
                    x + SEARCH_W + 4, y + 1, 0x3A3A3A, false);
        }
    }

    private int allInTab() {
        int n = 0;
        for (String concept : collectConcepts()) {
            if (selectedTab == null || AdaptationRegistrySafe.domainOf(concept) == selectedTab) {
                n++;
            }
        }
        return n;
    }

    private int countMatches() {
        return visibleEntries.size();
    }

    /**
     * Character index nearest an x offset.
     *
     * <p>{@code Font.plainSubstrByWidth} returns a String, not an index, so it cannot answer this.
     * Walking the characters is also what a click actually needs: it snaps to a boundary rather
     * than to whatever width happens to be under the cursor.
     */
    private int caretAt(String text, double offsetX) {
        if (offsetX <= 0) {
            return 0;
        }
        int index = text.length();
        for (int i = 1; i <= text.length(); i++) {
            if (this.font.width(text.substring(0, i)) > offsetX) {
                return i - 1;
            }
        }
        return index;
    }

    /**
     * Right-aligned, and BELOW the adaptation count rather than beside it.
     *
     * <p>The count badge already owns the top-right corner, so a field on the same row overlaps it
     * and the match counter has nowhere to go. Moving the field down one line is the smallest
     * change that keeps both readable; the alternative, shifting the count left, would leave the
     * title and the count fighting over the middle of the header.
     */
    private int[] searchHitbox() {
        int y = panelY + PAD + this.font.lineHeight
                + Math.max(0, (this.font.lineHeight - SEARCH_H) / 2);
        int x = panelX + panelW - PAD - SEARCH_W - MATCH_W;
        return new int[] {x, y, SEARCH_W, SEARCH_H};
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        tabHitboxes.clear();
        List<Component> labels = tabLabels();
        double maxTab = maxTabScroll();
        tabScroll = Math.max(0, Math.min(maxTab, tabScroll));
        int y = tabsY - (int) Math.round(tabScroll);
        int x = tabX;

        for (int i = 0; i < labels.size(); i++) {
            boolean selected = i == 0 ? selectedTab == ALL_TAB : tabs.get(i - 1) == selectedTab;
            String label = labels.get(i).getString();
            int w = tabW;
            boolean hovered = !selected && mouseX >= x && mouseX < x + w
                    && mouseY >= y && mouseY < y + TAB_H;

            int ty = selected ? y - 1 : y;
            int th = TAB_H + (selected ? 2 : 0);
            panel(g, x, ty, w, th, hovered ? PANEL_BODY_HOVER : PANEL_BODY);
            int textW = this.font.width(label);
            g.drawString(this.font, this.font.plainSubstrByWidth(label, w - 8),
                    x + Math.max(3, (w - textW) / 2), ty + 3,
                    selected ? TEXT_HEADER : (hovered ? TEXT : TEXT_DIM), false);
            tabHitboxes.add(new int[]{x, ty, w, th});
            y += TAB_H + 1;
        }
        if (maxTab > 0) {
            g.fill(x + tabW - 2, tabsY, x + tabW, tabsY + tabViewportH, WELL_SHADOW);
        }
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY, long now) {

        int wellX = listX - 4;
        int wellW = panelX + panelW - PAD - wellX;
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
        boolean on = ClientAdaption.isEnabled(concept);
        int accent = on ? onPanel(Concepts.color(concept)) : 0xFF808080;

        g.fill(x, y, x + w, y + CARD_H, CARD_DARK);
        g.fill(x, y, x + w - 1, y + CARD_H - 1, CARD_LIGHT);
        g.fill(x + 1, y + 1, x + w - 1, y + CARD_H - 1, hovered ? CARD_HOVER : CARD_BODY);

        g.fill(x + 1, y + 1, x + 4, y + CARD_H - 1, accent);
        g.fill(x + 1, y + 1, x + 2, y + CARD_H - 1, mix(accent, 0xFFFFFFFF, 0.35f));

        int contentX = x + 9;
        int contentW = w - 9 - 6 - TOGGLE_W;

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

        renderToggle(g, concept, x + w - TOGGLE_W - 4, y + (CARD_H - TOGGLE_H) / 2, on);
    }

    private void renderToggle(GuiGraphics g, String concept, int x, int y, boolean on) {
        int cy = y + TOGGLE_H / 2;
        int knobX = on ? x + TOGGLE_W - 5 : x + 2;
        boolean hovered = lastMouseX >= x - 2 && lastMouseX < x + TOGGLE_W + 2
                && lastMouseY >= y - 2 && lastMouseY < y + TOGGLE_H + 2;

        g.fill(x, y, x + TOGGLE_W, y + TOGGLE_H, 0xFF1A1A1A);
        g.fill(x + 1, y + 1, x + TOGGLE_W - 1, y + TOGGLE_H - 1,
                on ? (hovered ? 0xFF6FA84A : 0xFF4E7C33) : (hovered ? 0xFF5A5A5A : 0xFF3A3A3A));
        g.fill(knobX, cy - 3, knobX + 5, cy + 4, on ? 0xFFE8F5D8 : 0xFFB0B0B0);
        g.fill(knobX, cy - 3, knobX + 5, cy + 1, 0x66FFFFFF);
        g.fill(knobX, cy + 3, knobX + 5, cy + 4, 0x40000000);
    }

    private int[] toggleHitbox(String concept) {
        int index = visibleEntries.indexOf(concept);
        if (index < 0) {
            return null;
        }
        int y = listY + index * ENTRY_H - (int) Math.round(scrollOffset);
        int boxY = y + (CARD_H - TOGGLE_H) / 2;
        if (boxY + TOGGLE_H <= listY || boxY >= listY + listH) {
            return null;
        }
        return new int[]{listX + listW - TOGGLE_W - 6, boxY, TOGGLE_W + 4, TOGGLE_H};
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
        int right = panelX + panelW - PAD;
        if (!visibleEntries.isEmpty()) {
            String shown = visibleEntries.size() + "";
            g.drawString(this.font, shown, right - this.font.width(shown), y, TEXT_DIM, false);
            right -= this.font.width(shown) + 6;
        }
        String hint = Component.translatable("adaptionwheel.gui.footer_scroll").getString();
        int room = right - (panelX + PAD);
        if (this.font.width(hint) > room) {
            hint = this.font.plainSubstrByWidth(hint, room);
        }
        g.drawString(this.font, hint, panelX + PAD, y, TEXT_DIM, false);
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
