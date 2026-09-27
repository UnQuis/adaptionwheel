package ru.adaptionwheel.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import ru.adaptionwheel.adapt.AdaptationDomain;
import ru.adaptionwheel.category.Concepts;
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
 * In-game browser of every known adaptation: grouped by domain, with levels, one-time state, live
 * analysis progress and descriptions. Read-only mirror of the server-synced state
 * ({@link ClientAdaption}); opening it never pauses the game and closing returns to gameplay.
 *
 * <p>Drawn entirely from vanilla's own GUI textures rather than flat rectangles, because flat
 * rectangles are what made this look like a debug overlay instead of a Minecraft menu:</p>
 * <ul>
 *   <li>{@code container/generic_54.png} nine-sliced for the panel and each tab. Its frame is a
 *       3 px bevel — black outline, white highlight, then a flat {@code #C6C6C6} fill — and the
 *       fill is uniform, so the middle stretches to any size without artefacts.</li>
 *   <li>{@code hud/experience_bar_*} for progress. Vanilla's XP bar, sub-rected to the filled
 *       width exactly as {@code ExperienceBar} does it, tinted per concept.</li>
 *   <li>{@code container/creative_inventory/scroller.png} for the scrollbar thumb.</li>
 * </ul>
 *
 * <p>Text follows the vanilla GUI convention instead of the previous one: dark grey on the light
 * panel, because gold-on-black is legible in a HUD and unreadable on {@code #C6C6C6}. Concept
 * colours are darkened for the same reason, keeping the hue so the per-domain grouping the HUD
 * teaches still reads here.</p>
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

    private static final int BORDER = 3;
    private static final int BAR_H = 5;          // vanilla's bars are 5 px tall
    private static final int SCROLLER_W = 12;
    private static final int SCROLLER_H = 15;

    /** Vanilla GUI text colours. */
    private static final int TEXT = 0xFF404040;
    private static final int TEXT_DIM = 0xFF707070;
    private static final int TEXT_HEADER = 0xFF3F3F3F;

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
    /**
     * The concept set the current list was built from. The screen used to assemble its tabs and
     * rows once in {@code init()}, so opening it in the first second after joining showed an
     * empty list — the state arrives on the 1 Hz sync, which lands after the screen is already up.
     * Compared each frame instead, so the list fills in as soon as the data does.
     */
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

    public AdaptationScreen() {
        super(Component.translatable("adaptionwheel.gui.title"));
    }

    @Override
    protected void init() {
        panelW = Math.max(MIN_W, Math.min(360, this.width - 20));
        panelX = (this.width - panelW) / 2;
        panelY = Math.max(8, this.height / 2 - panelH() / 2);
        panelH = panelH();
        layoutList();
        lastConcepts = List.of();
        refreshIfStale();
    }

    private int panelH() {
        return Math.max(120, Math.min(260, this.height - 40));
    }

    private void layoutList() {
        listX = panelX + PAD;
        listY = panelY + PAD + this.font.lineHeight + 2 + TAB_H + 4;
        listW = panelW - PAD * 2 - SCROLLER_W - 2;
        listH = panelY + panelH - FOOTER_H - PAD - listY;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ================= data assembly =================

    /**
     * Rebuilds the tabs and rows when the synced concept set has actually changed.
     *
     * <p>Called every frame rather than once from {@code init()}, because the data is a mirror of
     * server state that arrives asynchronously: the window between the screen opening and the
     * first sync is exactly when a player who just joined would press the key, and a list built
     * eagerly came up empty with "Adaptations: 51" in the corner.</p>
     */
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < tabHitboxes.size(); i++) {
                int[] b = tabHitboxes.get(i);
                if (mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + TAB_H) {
                    selectedTab = i == 0 ? ALL_TAB : tabs.get(i - 1);
                    scrollOffset = 0;
                    refreshEntries();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(null);
    }

    // ================= rendering =================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        refreshIfStale();
        // Same dim the vanilla options and container screens put over the world.
        g.fill(0, 0, this.width, this.height, 0xC0101010);

        panel(g, panelX, panelY, panelW, panelH);
        renderHeader(g);
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
        g.drawString(this.font, Component.translatable("adaptionwheel.gui.footer_scroll"),
                panelX + PAD, panelY + panelH - FOOTER_H + 2, TEXT_DIM, false);
    }

    private void renderHeader(GuiGraphics g) {
        g.drawString(this.font, this.title, panelX + PAD, panelY + PAD, TEXT_HEADER, false);
        String count = Component.translatable("adaptionwheel.gui.count", ClientAdaption.adaptedCount).getString();
        g.drawString(this.font, count,
                panelX + panelW - PAD - this.font.width(count), panelY + PAD, TEXT_DIM, false);
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        tabHitboxes.clear();
        int y = panelY + PAD + this.font.lineHeight + 2;
        int x = panelX + PAD - BORDER;

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
            g.drawString(this.font, label, x + PAD - 1, ty + 3,
                    selected ? TEXT_HEADER : (hovered ? TEXT : TEXT_DIM), false);
            tabHitboxes.add(new int[]{x, ty, w});
            x += w + 1;
        }
    }

    private void renderEntry(GuiGraphics g, String concept, int y) {
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
        g.drawString(this.font, state, listX + contentW - this.font.width(state), y, stateColor, false);

        String name = Concepts.displayName(concept).getString();
        g.drawString(this.font, this.font.plainSubstrByWidth(name, contentW - this.font.width(state) - 6),
                listX, y, accent, false);

        bar(g, listX, y + 11, contentW, barFill);

        List<FormattedCharSequence> desc = descriptionCache.computeIfAbsent(concept, k -> {
            Component text = Component.translatableWithFallback("adaptionwheel.desc." + k, "");
            return text.getString().isEmpty() ? List.of() : this.font.split(text, contentW);
        });
        for (int i = 0; i < desc.size() && i < 2; i++) {
            g.drawString(this.font, desc.get(i), listX, y + 18 + i * 9, TEXT_DIM, false);
        }
    }

    /**
     * A progress bar in vanilla's own bar colours.
     *
     * <p>Drawn with fills rather than blitting {@code experience_bar_*} on purpose. The sprite is
     * the right one, but {@code GuiGraphics.blitSprite}'s overloads cannot be read reliably from
     * decompiled sources (the parameter names are meaningless and two overloads differ only in
     * arity), and guessing wrong produced a bar whose background and fill landed at different
     * widths — a bar split in half with a gap — and a magenta tint. These four colours are sampled
     * straight out of {@code experience_bar_background.png} and {@code experience_bar_progress.png},
     * so the result is the vanilla bar at 5 px with no dependency on a signature I cannot verify.</p>
     */
    private void bar(GuiGraphics g, int x, int y, int width, float fill) {
        g.fill(x, y, x + width, y + BAR_H, BAR_FRAME);
        g.fill(x + 1, y + 1, x + width - 1, y + BAR_H - 1, BAR_EMPTY);
        int fillW = Math.round((width - 2) * Math.max(0f, Math.min(1f, fill)));
        if (fillW > 0) {
            g.fill(x + 1, y + 1, x + 1 + fillW, y + BAR_H - 1, BAR_FULL);
        }
    }

    /** Scrollbar knob, same reasoning as {@link #bar}. */
    private void renderScrollbar(GuiGraphics g) {
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

    // ================= panel =================

    /**
     * One of vanilla's container panels at an arbitrary size.
     *
     * <p>Drawn from the exact pixel values read out of
     * {@code textures/gui/container/generic_54.png}: a 1 px {@code #000000} outline, a 2 px
     * {@code #FFFFFF} highlight inset by one, and a flat {@code #C6C6C6} body from offset 3. That
     * texture is 256x256 but only its top-left corner carries the bevel — everything right of
     * ~176 and below ~166 is black padding — so sampling it per corner is not an option, and
     * reconstructing the bevel from its own colours is not a downgrade: the result is
     * byte-identical to the panel vanilla draws, with no stretching artefacts and no dependence
     * on which {@code blit} overload happens to be right.</p>
     */
    private void panel(GuiGraphics g, int x, int y, int w, int h) {
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
     * Concept colours are chosen to read against a dark HUD, where they are used elsewhere and
     * must not change. On this light panel they would be near-invisible, so the same hue is scaled
     * down rather than replaced.
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
