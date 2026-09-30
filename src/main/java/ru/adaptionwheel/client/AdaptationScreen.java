package ru.adaptionwheel.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
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

/**
 * In-game browser of every known adaptation, drawn in vanilla's GUI style: a container panel
 * (1 px {@code #000000} outline, 2 px {@code #FFFFFF} highlight, flat {@code #C6C6C6} body — the
 * exact pixel values read out of {@code textures/gui/container/generic_54.png}), domain tabs
 * raised two pixels when selected the way vanilla's own are, dark {@code #404040} text on the
 * light panel, and progress bars in vanilla's XP-bar colours.
 *
 * <p>Concept colours are scaled to 52 % for the light panel ({@link #onPanel}) so the per-domain
 * grouping the HUD teaches still reads here; the HUD's own colours are untouched.
 *
 * <p>The bars and scrollbar are drawn with {@code fill()} rather than blitting
 * {@code experience_bar_*}. On 1.21.1 that was forced: {@code blitSprite}'s overloads cannot be read
 * reliably from decompiled sources and a wrong guess rendered a bar split in half plus a magenta
 * tint. Same reasoning here — sampled colours are deterministic.</p>
 *
 * <p>The list is rebuilt per frame when the synced concept set changes rather than once in
 * {@code init()}: the client mirror arrives on the 1 Hz sync, so a screen opened in the first
 * second after joining showed an empty list under a header reading "Adaptations: 51".</p>
 *
 * <p>26.3 renders screens through {@link GuiGraphicsExtractor#extractRenderState} rather than
 * {@code render(GuiGraphics, ...)}; text is drawn with {@code g.text(...)}.</p>
 */
public class AdaptationScreen extends Screen {

    /** Colours read out of {@code textures/gui/container/generic_54.png}. */
    private static final int PANEL_EDGE = 0xFF000000;
    private static final int PANEL_HILIGHT = 0xFFFFFFFF;
    private static final int PANEL_BODY = 0xFFC6C6C6;

    /** Colours sampled from vanilla's XP bar sprites. */
    private static final int BAR_FRAME = 0xFF09100C;
    private static final int BAR_EMPTY = 0xFF28332D;
    private static final int BAR_FULL = 0xFF71A549;

    /** Vanilla GUI text colours. */
    private static final int TEXT = 0xFF404040;
    private static final int TEXT_DIM = 0xFF707070;
    private static final int TEXT_HEADER = 0xFF3F3F3F;

    private static final int BAR_H = 5;
    private static final int SCROLLER_W = 12;
    private static final int SCROLLER_H = 15;
    private static final int TAB_H = 14;
    private static final int FOOTER_H = 12;
    private static final int ENTRY_H = 34;
    private static final int PAD = 7;
    private static final int MIN_W = 200;

    /** Null domain = the ALL tab. */
    private static final AdaptationDomain ALL_TAB = null;

    private final List<AdaptationDomain> tabs = new ArrayList<>();
    /** x, y, width per tab, in tab order. */
    private final List<int[]> tabHitboxes = new ArrayList<>();
    private final List<String> visibleEntries = new ArrayList<>();
    private final Map<String, List<FormattedCharSequence>> descriptionCache = new HashMap<>();

    private AdaptationDomain selectedTab = ALL_TAB;
    /** The concept set the current list was built from, so it can be rebuilt when it changes. */
    private List<String> lastConcepts = List.of();
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private double scrollOffset;

    /** Active synergy ids and the wrapped lines they render as, refreshed with the row list. */
    private final List<String> activeSynergies = new ArrayList<>();
    private final List<String> synergyLines = new ArrayList<>();

    /**
     * Vertical space the synergy strip occupies, 0 when none are active.
     *
     * <p>A field rather than a constant because it feeds {@code layoutList()}: the list has to
     * start below the strip, and the strip is a different height every time a synergy lights up.</p>
     */
    private int synergyStripH;

    public AdaptationScreen() {
        super(Component.translatable("adaptionwheel.gui.title"));
    }

    @Override
    protected void init() {
        panelW = Math.max(MIN_W, Math.min(360, this.width - 20));
        panelX = (this.width - panelW) / 2;
        panelH = panelH();
        panelY = Math.max(8, this.height / 2 - panelH / 2);
        layoutList();
        lastConcepts = List.of();
        refreshIfStale();
    }

    private int panelH() {
        return Math.max(120, Math.min(260, this.height - 40));
    }

    /**
     * Places the row list under the header, the synergy strip and the tabs.
     *
     * <p>Its own method, and called every frame rather than once from {@code init()}, because the
     * strip is a different height whenever a synergy lights up or goes out. Leaving it inline
     * meant the list rectangle could not follow it, so a new synergy would either clip the last
     * row or leave a gap.</p>
     */
    private void layoutList() {
        listX = panelX + PAD;
        listY = panelY + PAD + this.font.lineHeight + 2 + synergyStripH + TAB_H + 4;
        listW = panelW - PAD * 2 - SCROLLER_W - 2;
        listH = panelY + panelH - FOOTER_H - PAD - listY;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ================= data assembly =================

    /** Rebuilds the tabs and rows when the synced concept set has actually changed. */
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
        scrollOffset = Math.min(scrollOffset, maxScroll());
    }

    private double maxScroll() {
        return Math.max(0, visibleEntries.size() * ENTRY_H - listH);
    }

    // ================= input =================

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= listX && mouseX < listX + listW + SCROLLER_W
                && mouseY >= listY && mouseY < listY + listH) {
            scrollOffset = Math.max(0, Math.min(maxScroll(), scrollOffset - scrollY * ENTRY_H));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        for (int i = 0; i < tabHitboxes.size(); i++) {
            int[] b = tabHitboxes.get(i);
            if (mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + TAB_H) {
                selectedTab = i == 0 ? ALL_TAB : tabs.get(i - 1);
                scrollOffset = 0;
                refreshEntries();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(null); // 26.3 routes screen changes through the Gui
    }

    // ================= rendering =================

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        refreshIfStale();
        layoutSynergies();
        // Relaid out every frame rather than once in init(): a synergy can light up or go out
        // while the screen is open, and a cached list rectangle would then either clip the last
        // row or leave a gap. The scissor and the scroll bounds all read these fields, so one
        // recompute keeps the whole frame consistent.
        layoutList();
        // Same dim the vanilla options and container screens put over the world.
        g.fill(0, 0, this.width, this.height, 0xC0101010);

        panel(g, panelX, panelY, panelW, panelH);
        renderHeader(g);
        renderSynergyStrip(g);
        renderTabs(g, mouseX, mouseY);

        g.enableScissor(listX, listY, listX + listW + SCROLLER_W, listY + listH);
        int y = listY - (int) scrollOffset;
        for (String concept : visibleEntries) {
            if (y + ENTRY_H >= listY && y <= listY + listH) {
                renderEntry(g, concept, y);
            }
            y += ENTRY_H;
        }
        g.disableScissor();

        renderScrollbar(g);
        g.text(this.font, Component.translatable("adaptionwheel.gui.footer_scroll"),
                panelX + PAD, panelY + panelH - FOOTER_H + 2, TEXT_DIM, false);
    }

    private void renderHeader(GuiGraphicsExtractor g) {
        g.text(this.font, this.title, panelX + PAD, panelY + PAD, TEXT_HEADER, false);
        String count = Component.translatable("adaptionwheel.gui.count", ClientAdaption.adaptedCount).getString();
        g.text(this.font, count, panelX + panelW - PAD - this.font.width(count), panelY + PAD,
                TEXT_DIM, false);
    }

    /**
     * Recomputes which synergies are active and wraps their names into lines.
     *
     * <p>Reads the same {@code Synergies.satisfied} the server uses, on the synced mirror, so the
     * browser cannot show a synergy the server disagrees with. Called per frame because the input
     * set can change at any time; the roster is ten entries, so the cost is not worth caching.</p>
     */
    private void layoutSynergies() {
        activeSynergies.clear();
        for (Synergies.Synergy synergy : Synergies.ALL) {
            if (Synergies.satisfied(ClientAdaption.LEVELS, ClientAdaption.ADAPTED, synergy)) {
                activeSynergies.add(synergy.id());
            }
        }
        synergyLines.clear();
        int avail = panelW - PAD * 2;
        for (String id : activeSynergies) {
            String name = Component.translatable("adaptionwheel.synergy." + id).getString();
            if (synergyLines.isEmpty()) {
                synergyLines.add(name);
                continue;
            }
            int last = synergyLines.size() - 1;
            String joined = synergyLines.get(last) + "  " + name;
            if (this.font.width(joined) <= avail) {
                synergyLines.set(last, joined);
            } else {
                synergyLines.add(name);
            }
        }
        synergyStripH = synergyLines.isEmpty() ? 0 : synergyLines.size() * this.font.lineHeight;
    }

    /**
     * The synergies the wearer currently holds, on one or two lines under the header.
     *
     * <p>A strip rather than a tab, because a synergy is not an adaptation: it has no level, is
     * never adapted directly, and would look broken sitting in a list where everything else has
     * one. Drawn in the same dim grey as the secondary text so it reads as annotation on the
     * header rather than as a row of the list.</p>
     */
    private void renderSynergyStrip(GuiGraphicsExtractor g) {
        if (synergyLines.isEmpty()) {
            return;
        }
        int y = panelY + PAD + this.font.lineHeight + 2;
        for (String line : synergyLines) {
            g.text(this.font, line, panelX + PAD, y, TEXT_DIM, false);
            y += this.font.lineHeight;
        }
    }

    private void renderTabs(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        tabHitboxes.clear();
        int y = panelY + PAD + this.font.lineHeight + 2;
        int x = panelX + PAD - 3;

        List<Component> labels = new ArrayList<>();
        labels.add(Component.translatable("adaptionwheel.gui.tab_all"));
        for (AdaptationDomain domain : tabs) {
            labels.add(domain.translation());
        }
        for (int i = 0; i < labels.size(); i++) {
            boolean selected = i == 0 ? selectedTab == ALL_TAB : tabs.get(i - 1) == selectedTab;
            String label = labels.get(i).getString();
            int w = this.font.width(label) + PAD * 2 - 1;
            boolean hovered = !selected && mouseX >= x && mouseX < x + w
                    && mouseY >= y && mouseY < y + TAB_H;
            // A selected tab sits two pixels higher, the way vanilla's own tabs read as "raised".
            int ty = selected ? y - 2 : y;
            panel(g, x, ty, w, TAB_H + (selected ? 2 : 0));
            g.text(this.font, label, x + PAD - 1, ty + 3,
                    selected ? TEXT_HEADER : (hovered ? TEXT : TEXT_DIM), false);
            tabHitboxes.add(new int[]{x, ty, w});
            x += w + 1;
        }
    }

    private void renderEntry(GuiGraphicsExtractor g, String concept, int y) {
        int contentW = listW;
        int accent = onPanel(Concepts.color(concept));

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
        if (runningTask != null) {
            float p = ClientAdaption.taskProgress(runningTask);
            state = Component.translatable("adaptionwheel.gui.analyzing").getString()
                    + " " + (int) (p * 100) + "%";
            barFill = p;
            stateColor = TEXT;
        } else if (!leveled && adapted) {
            state = Component.translatable("adaptionwheel.gui.adapted").getString();
            barFill = 1f;
            stateColor = TEXT_HEADER;
        } else if (leveled && level >= PlayerAdaption.MAX_LEVEL) {
            state = Component.translatable("adaptionwheel.gui.max_reached").getString();
            barFill = 1f;
            stateColor = TEXT_HEADER;
        } else if (leveled) {
            state = Component.translatable("adaptionwheel.gui.level", level,
                    PlayerAdaption.MAX_LEVEL).getString();
            barFill = level / (float) PlayerAdaption.MAX_LEVEL;
            stateColor = TEXT_DIM;
        } else {
            state = Component.translatable("adaptionwheel.gui.pending").getString();
            barFill = 0f;
            stateColor = TEXT_DIM;
        }
        g.text(this.font, state, listX + contentW - this.font.width(state), y, stateColor, false);

        String name = Concepts.displayName(concept).getString();
        g.text(this.font, this.font.plainSubstrByWidth(name, contentW - this.font.width(state) - 6),
                listX, y, accent, false);

        bar(g, listX, y + 11, contentW, barFill);

        List<FormattedCharSequence> desc = descriptionCache.computeIfAbsent(concept, k -> {
            Component text = Component.translatableWithFallback("adaptionwheel.desc." + k, "");
            return text.getString().isEmpty() ? List.of() : this.font.split(text, contentW);
        });
        for (int i = 0; i < desc.size() && i < 2; i++) {
            g.text(this.font, desc.get(i), listX, y + 18 + i * 9, TEXT_DIM, false);
        }
    }

    private void bar(GuiGraphicsExtractor g, int x, int y, int width, float fill) {
        g.fill(x, y, x + width, y + BAR_H, BAR_FRAME);
        g.fill(x + 1, y + 1, x + width - 1, y + BAR_H - 1, BAR_EMPTY);
        int fillW = Math.round((width - 2) * Math.max(0f, Math.min(1f, fill)));
        if (fillW > 0) {
            g.fill(x + 1, y + 1, x + 1 + fillW, y + BAR_H - 1, BAR_FULL);
        }
    }

    private void renderScrollbar(GuiGraphicsExtractor g) {
        double max = maxScroll();
        if (max <= 0 || listH <= SCROLLER_H) {
            return;
        }
        int trackX = listX + listW + 2;
        int travel = listH - SCROLLER_H;
        int thumbY = listY + (int) (travel * (scrollOffset / max));
        g.fill(trackX, thumbY, trackX + SCROLLER_W, thumbY + SCROLLER_H, PANEL_EDGE);
        g.fill(trackX + 1, thumbY + 1, trackX + SCROLLER_W - 1, thumbY + SCROLLER_H - 1, PANEL_HILIGHT);
    }

    /**
     * One of vanilla's container panels at an arbitrary size. The frame is a 3 px bevel — black
     * outline, white highlight, flat fill — reconstructed from the texture's own pixel values
     * rather than sampled per corner, because only its top-left corner actually carries the bevel
     * (everything right of ~176 and below ~166 is black padding).
     */
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

    /**
     * Concept colours are chosen to read against a dark HUD and must not change there. On this
     * light panel they would be near-invisible, so the same hue is scaled down instead.
     */
    private static int onPanel(int rgb) {
        float f = 0.52f;
        int r = Math.round(((rgb >> 16) & 0xFF) * f);
        int g = Math.round(((rgb >> 8) & 0xFF) * f);
        int b = Math.round((rgb & 0xFF) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Indirection so the screen does not hard-depend on registry class init order. */
    private static final class AdaptationRegistrySafe {
        static ru.adaptionwheel.adapt.AdaptationDomain domainOf(String concept) {
            return ru.adaptionwheel.adapt.AdaptationRegistry.domainOf(concept);
        }
    }
}
