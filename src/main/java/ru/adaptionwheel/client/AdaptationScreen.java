package ru.adaptionwheel.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import ru.adaptionwheel.adapt.AdaptationDomain;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.data.AdaptionTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-game browser of every known adaptation: grouped by domain, with levels,
 * one-time state, live analysis progress and descriptions. Read-only mirror of
 * the server-synced state ({@link ClientAdaption}); opening it never pauses the
 * game and closing returns straight to gameplay.
 */
public class AdaptationScreen extends Screen {

    private static final int PANEL_W = 320;
    private static final int HEADER_H = 26;
    private static final int TAB_H = 15;
    private static final int FOOTER_H = 13;
    private static final int ENTRY_H = 34;
    private static final int PAD = 8;

    /** Null domain = the ALL tab. */
    private static final AdaptationDomain ALL_TAB = null;

    private final List<AdaptationDomain> tabs = new ArrayList<>();
    private final List<int[]> tabHitboxes = new ArrayList<>(); // x, width
    private final List<String> visibleEntries = new ArrayList<>();
    private final Map<String, List<FormattedCharSequence>> descriptionCache = new HashMap<>();

    private AdaptationDomain selectedTab = ALL_TAB;
    private int panelX;
    private int panelY;
    private int panelH;
    private int listY;
    private int listH;
    private double scrollOffset;

    public AdaptationScreen() {
        super(Component.translatable("adaptionwheel.gui.title"));
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_W) / 2;
        panelY = Math.max(16, this.height / 8);
        panelH = this.height - panelY - Math.max(16, this.height / 8);
        rebuildTabs();
        refreshEntries();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ================= data assembly =================

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
        return Math.max(0, visibleEntries.size() * ENTRY_H - listContentHeight());
    }

    private int listContentHeight() {
        return listH;
    }

    // ================= input =================

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= panelX && mouseX < panelX + PANEL_W && mouseY >= listY && mouseY < listY + listH) {
            scrollOffset = Math.max(0, Math.min(maxScroll(), scrollOffset - scrollY * ENTRY_H));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (event.button() == 0 && mouseY >= panelY + HEADER_H && mouseY < panelY + HEADER_H + TAB_H) {
            for (int i = 0; i < tabHitboxes.size(); i++) {
                int[] box = tabHitboxes.get(i);
                if (mouseX >= box[0] && mouseX < box[0] + box[1]) {
                    selectedTab = i == 0 ? ALL_TAB : tabs.get(i - 1);
                    scrollOffset = 0;
                    refreshEntries();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isInGameUi() {
        // Transparent in-game background (no blur), like the 1.21 renderBackground() default.
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(null);
    }

    // ================= rendering =================

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        listY = panelY + HEADER_H + TAB_H + PAD;
        listH = panelH - (listY - panelY) - FOOTER_H;

        // Panel backdrop
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, 0xD0101018);
        g.outline(panelX, panelY, PANEL_W, panelH, 0xFFD4A017);

        renderHeader(g, mouseX, mouseY);
        renderTabs(g, mouseX, mouseY);

        g.enableScissor(panelX + 1, listY, panelX + PANEL_W - 1, listY + listH);
        int y = listY + 2 - (int) scrollOffset;
        for (String concept : visibleEntries) {
            if (y + ENTRY_H >= listY && y <= listY + listH) {
                renderEntry(g, concept, y);
            }
            y += ENTRY_H;
        }
        g.disableScissor();

        renderScrollbar(g);
        g.text(this.font,
                Component.translatable("adaptionwheel.gui.footer_scroll"),
                panelX + PAD, panelY + panelH - FOOTER_H + 3, 0xFF777777, false);
    }

    private void renderHeader(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(this.font, this.title, panelX + PAD, panelY + PAD, 0xFFFFD700, true);
        String count = Component.translatable("adaptionwheel.gui.count", ClientAdaption.adaptedCount).getString();
        g.text(this.font, count,
                panelX + PANEL_W - PAD - this.font.width(count), panelY + PAD, 0xFFBBBBBB, true);
    }

    private void renderTabs(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        tabHitboxes.clear();
        int x = panelX + PAD;
        int y = panelY + HEADER_H;

        // ALL tab first, then domains that actually have entries.
        List<Component> labels = new ArrayList<>();
        labels.add(Component.translatable("adaptionwheel.gui.tab_all"));
        for (AdaptationDomain domain : tabs) {
            labels.add(domain.translation());
        }
        for (int i = 0; i < labels.size(); i++) {
            boolean selected = i == 0 ? selectedTab == ALL_TAB : tabs.get(i - 1) == selectedTab;
            String label = labels.get(i).getString();
            int w = this.font.width(label) + 10;
            int bg = selected ? 0xFF3A3A22 : 0xFF222230;
            g.fill(x, y, x + w, y + TAB_H - 2, bg);
            int fg = selected ? 0xFFFFE08A : 0xFFAAAAAA;
            g.text(this.font, label, x + 5, y + 3, fg, false);
            tabHitboxes.add(new int[]{x, w});
            x += w + 4;
        }
    }

    private void renderEntry(GuiGraphicsExtractor g, String concept, int y) {
        int x = panelX + PAD;
        int contentW = PANEL_W - PAD * 2 - 6;
        int domainColor = 0xFF000000 | Concepts.color(concept);

        // Live values from the synced mirror
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

        // State text (right aligned) — localized via gui.* keys
        String state;
        float barFill;
        int stateColor;
        if (runningTask != null) {
            float p = ClientAdaption.taskProgress(runningTask);
            state = Component.translatable("adaptionwheel.gui.analyzing").getString()
                    + " " + (int) (p * 100) + "%";
            barFill = p;
            stateColor = 0xFFFFD700;
        } else if (!leveled && adapted) {
            // One-time adaptations are binary: no levels at all.
            state = Component.translatable("adaptionwheel.gui.adapted").getString();
            barFill = 1f;
            stateColor = 0xFF7CFC00;
        } else if (leveled && level >= 8) {
            state = Component.translatable("adaptionwheel.gui.max_reached").getString();
            barFill = 1f;
            stateColor = 0xFF7CFC00;
        } else if (leveled) {
            state = Component.translatable("adaptionwheel.gui.level", level, 8).getString();
            barFill = level / 8f;
            stateColor = 0xFF999999;
        } else {
            // One-time, not yet adapted and not analyzing.
            state = Component.translatable("adaptionwheel.gui.pending").getString();
            barFill = 0f;
            stateColor = 0xFF999999;
        }
        g.text(this.font, state, panelX + PANEL_W - PAD - this.font.width(state),
                y + 2, stateColor, true);

        // Name
        String name = Concepts.displayName(concept).getString();
        g.text(this.font, this.font.plainSubstrByWidth(name, contentW - this.font.width(state) - 8),
                x, y + 2, domainColor, true);

        // Progress bar
        int barY = y + 12;
        g.fill(x, barY, x + contentW, barY + 5, 0xFF000000);
        int fillW = (int) (contentW * barFill);
        if (fillW > 0) {
            g.fill(x, barY, x + fillW, barY + 5, domainColor);
        }

        // Description (up to two wrapped lines)
        List<FormattedCharSequence> desc = descriptionCache.computeIfAbsent(concept, k -> {
            Component text = Component.translatableWithFallback("adaptionwheel.desc." + k, "");
            return text.getString().isEmpty()
                    ? List.of()
                    : this.font.split(text, contentW);
        });
        int dy = barY + 7;
        for (int i = 0; i < desc.size() && i < 2; i++) {
            g.text(this.font, desc.get(i), x, dy + i * 9, 0xFF8A8A8A, false);
        }
    }

    private void renderScrollbar(GuiGraphicsExtractor g) {
        double max = maxScroll();
        if (max <= 0 || listH <= 0) {
            return;
        }
        int trackX = panelX + PANEL_W - 5;
        float ratio = (float) (listH / (double) (listH + max));
        int thumbH = Math.max(12, (int) (listH * ratio));
        int thumbY = listY + (int) ((listH - thumbH) * (scrollOffset / max));
        g.fill(trackX, listY, trackX + 3, listY + listH, 0xFF1A1A24);
        g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xFFD4A017);
    }

    /** Indirection so the screen does not hard-depend on registry class init order. */
    private static final class AdaptationRegistrySafe {
        static ru.adaptionwheel.adapt.AdaptationDomain domainOf(String concept) {
            return ru.adaptionwheel.adapt.AdaptationRegistry.domainOf(concept);
        }
    }
}
