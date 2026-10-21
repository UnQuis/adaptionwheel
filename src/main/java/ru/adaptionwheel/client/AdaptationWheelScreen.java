package ru.adaptionwheel.client;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import ru.adaptionwheel.ability.ActiveAbility;
import ru.adaptionwheel.ability.ActiveAbilities;
import ru.adaptionwheel.adapt.AdaptationDomain;
import ru.adaptionwheel.adapt.AdaptationRegistry;
import ru.adaptionwheel.category.CombatFistTiers;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.category.FistTiers;
import ru.adaptionwheel.category.Synergies;
import ru.adaptionwheel.data.AdaptionTask;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.item.ModItems;
import ru.adaptionwheel.network.ToggleAdaptationPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * The dark editorial adaptation menu.
 *
 * <p>This is the wheel's only browser. It lists the concepts the player already knows, grouped by
 * domain, with a search field, a vertical domain rail and the same server-authoritative switches
 * the rest of the mod uses. A compact wheel card on the left carries the identity of the item; the
 * right column is the working surface. The layout is responsive: a narrow window drops the left
 * card and keeps the domain rail, rather than hiding the tabs behind a second menu.
 *
 * <p>Nothing is drawn with a sprite. {@link WheelUi} supplies the rounded cards, the hairlines and
 * the typographic helpers, and every colour is an ARGB literal from that vocabulary. The reason is
 * in {@code WheelUi}'s javadoc: a vanilla nine-slice has no soft shadow and no 1 px hairline at 2x
 * scale, and blitting a scaled sprite to fake one is how the previous panel acquired a magenta tint.
 */
public final class AdaptationWheelScreen extends Screen {

    private static final int CARD_HEIGHT = 58;
    private static final int CARD_GAP = 6;
    private static final int CARD_STRIDE = CARD_HEIGHT + CARD_GAP;
    private static final int GROUP_HEIGHT = 26;
    private static final int ABILITY_HEIGHT = 64;
    private static final int ABILITY_ROW_HEIGHT = 36;
    private static final int ABILITY_ROW_GAP = 6;
    private static final int TAB_HEIGHT = 20;
    private static final int TAB_GAP = 8;
    private static final int SYNERGY_HEIGHT = 18;
    private static final int MAX_LEVEL = PlayerAdaption.MAX_LEVEL;

    private static final int INK = WheelUi.INK;
    private static final int INK_SOFT = WheelUi.INK_SOFT;
    private static final int MUTED = WheelUi.MUTED;
    private static final int FAINT = WheelUi.FAINT;
    private static final int HAIRLINE = WheelUi.HAIRLINE;
    private static final int CARD = WheelUi.CARD;
    private static final int CARD_ALT = WheelUi.CARD_ALT;
    private static final int GREEN = WheelUi.GREEN;
    private static final int GREEN_BG = WheelUi.GREEN_BG;
    private static final int ROSE = WheelUi.ROSE;
    private static final int ROSE_BG = WheelUi.ROSE_BG;
    private static final int GOLD = WheelUi.GOLD;

    private final List<Row> rows = new ArrayList<>();
    private final List<CardView> cards = new ArrayList<>();
    private final List<AbilityHit> abilityHits = new ArrayList<>();
    private final List<TabView> tabHits = new ArrayList<>();
    private final List<String> known = new ArrayList<>();
    private final List<String> visible = new ArrayList<>();
    private final List<AdaptationDomain> availableDomains = new ArrayList<>();
    private final List<Synergies.Synergy> activeSynergies = new ArrayList<>();
    private final Map<String, Component> names = new HashMap<>();
    private final Map<String, Component> descriptions = new HashMap<>();
    private final Map<String, ItemStack> icons = new HashMap<>();
    private final List<int[]> specks = new ArrayList<>();

    private final long openedAt;
    private int lastSignature = Integer.MIN_VALUE;
    private int lastMouseX;
    private int lastMouseY;

    private AdaptationDomain selectedTab;
    private String selected;
    private String hovered;
    private String search = "";
    private int searchCaret;
    private int searchStart;
    private boolean searchFocused;
    private boolean draggingScrollbar;
    private double scroll;
    private double scrollTarget;
    private double scrollGrab;
    private double tabScroll;
    private int titleY;
    private int titleSize = 30;
    private float titleTracking = 7f;
    private ResourceLocation titleFont = WheelUi.MONO_30;
    private String titleText = "";
    private int titleWidth;
    private int ruleY;
    private int contentTop;
    private int footerY;
    private int margin;
    private boolean twoColumn;
    private boolean compactLeft;
    private int leftX;
    private int leftY;
    private int leftW;
    private int leftH;
    private int tabX;
    private int tabY;
    private int tabW;
    private int tabViewportH;
    private int listX;
    private int listW;
    private int listTop;
    private int listBottom;
    private int synergyTop;
    private int synergyStripH;
    private int abilityTop;
    private int abilityAreaH;
    private int adaptLabelY;
    private int scrollViewportTop;
    private int scrollViewportBottom;
    private Rect searchRect = Rect.EMPTY;
    private Rect clearRect = Rect.EMPTY;
    private Rect trackHit = Rect.EMPTY;
    private Rect leftToggle = Rect.EMPTY;
    private Rect placeholderRect = Rect.EMPTY;
    private ItemStack wheelIcon;

    public AdaptationWheelScreen() {
        super(Component.translatable("adaptionwheel.gui.menu_title"));
        this.openedAt = Util.getMillis();
    }

    @Override
    protected void init() {
        super.init();
        buildSpecks();
        lastSignature = signature();
        refreshRows(true);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        int current = signature();
        if (current != lastSignature) {
            lastSignature = current;
            refreshRows(false);
        }

        layout();
        updateScrollAnimation();
        updateHover(mouseX, mouseY);

        renderBackground(graphics, mouseX, mouseY, partialTick);
        drawHeader(graphics);
        drawSearch(graphics);
        if (twoColumn) {
            drawLeftCard(graphics);
            drawFloatingWheel(graphics);
        }
        drawTabs(graphics);
        drawRightColumn(graphics);
        drawFooter(graphics);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        final int bands = 32;
        for (int i = 0; i < bands; i++) {
            int color = WheelUi.mix(0xFF070708, 0xFF111114, i / (float) (bands - 1));
            int y0 = i * height / bands;
            int y1 = (i + 1) * height / bands + 1;
            graphics.fill(0, y0, width, y1, color);
        }

        // The reference is dark paper, not a flat fill. These specks are deliberately almost
        // invisible: they show up as texture on a bright monitor and disappear on a dim one, which
        // is exactly the amount of noise an editorial page wants. They drift upwards at four
        // different rates so the page reads as moving paper rather than as a static noise texture,
        // and the speeds are per-index instead of random so the field is reproducible.
        long now = Util.getMillis();
        long age = now - openedAt;
        for (int i = 0; i < specks.size(); i++) {
            int[] speck = specks.get(i);
            float alpha = 0.012f + (i % 3) * 0.008f;
            int size = speck[2];
            int drift = (int) (age * (1 + i % 4) / 1400L);
            int y = Math.floorMod(speck[1] - drift, Math.max(1, height));
            graphics.fill(speck[0], y, speck[0] + size, y + size,
                    WheelUi.withAlpha(0xF2EFE7, alpha));
        }

        // Slow vertical scan lines and a wide travelling sheen. They are intentionally much
        // quieter than the menu's content: the panel should feel alive, not animated over.
        int scanOffset = (int) (age / 45L % 6L);
        for (int y = scanOffset; y < height; y += 6) {
            graphics.fill(0, y, width, y + 1, 0x08FFFFFF);
        }
        int sweep = (int) (((now - openedAt) * 0.055) % (width + 300L)) - 150;
        int from = Math.max(0, sweep - 140);
        int to = Math.min(width, sweep + 140);
        for (int x = from; x < to; x += 2) {
            float distance = Math.abs(x - sweep) / 140f;
            float alpha = (1f - distance) * (1f - distance) * 0.035f;
            graphics.fill(x, 0, x + 2, height, WheelUi.withAlpha(0xFFFFFF, alpha));
        }
    }

    // ---------------------------------------------------------------- state

    private void buildSpecks() {
        specks.clear();
        Random random = new Random((long) width * 73856093L ^ (long) height * 19349663L);
        int count = Math.max(90, width * height / 5200);
        for (int i = 0; i < count; i++) {
            specks.add(new int[]{random.nextInt(Math.max(1, width)), random.nextInt(Math.max(1, height)),
                    1 + random.nextInt(2)});
        }
    }

    private int signature() {
        return Objects.hash(ClientAdaption.ADAPTED, ClientAdaption.LEVELS, ClientAdaption.TASKS.size(),
                ClientAdaption.DISABLED, ClientAdaption.wearingWheel, ClientAdaption.adaptedCount);
    }

    private void refreshRows(boolean resetScroll) {
        known.clear();
        known.addAll(ClientAdaption.LEVELS.keySet());
        known.addAll(ClientAdaption.ADAPTED);
        for (AdaptionTask task : ClientAdaption.TASKS) {
            if (task != null && task.concept != null && !task.concept.isBlank()) {
                known.add(task.concept);
            }
        }

        Set<String> unique = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        unique.addAll(known);
        known.clear();
        known.addAll(unique);

        availableDomains.clear();
        for (AdaptationDomain domain : AdaptationDomain.values()) {
            for (String concept : known) {
                if (AdaptationRegistry.domainOf(concept) == domain) {
                    availableDomains.add(domain);
                    break;
                }
            }
        }
        if (selectedTab != null && !availableDomains.contains(selectedTab)) {
            selectedTab = null;
        }

        activeSynergies.clear();
        for (Synergies.Synergy synergy : Synergies.ALL) {
            if (Synergies.satisfied(ClientAdaption.LEVELS, ClientAdaption.ADAPTED,
                    ClientAdaption.DISABLED, synergy)) {
                activeSynergies.add(synergy);
            }
        }

        visible.clear();
        for (String concept : known) {
            if (selectedTab != null && AdaptationRegistry.domainOf(concept) != selectedTab) {
                continue;
            }
            if (matches(concept)) {
                visible.add(concept);
            }
        }

        rows.clear();
        if (selectedTab == null) {
            for (AdaptationDomain domain : AdaptationDomain.values()) {
                List<String> group = new ArrayList<>();
                for (String concept : visible) {
                    if (AdaptationRegistry.domainOf(concept) == domain) {
                        group.add(concept);
                    }
                }
                if (group.isEmpty()) {
                    continue;
                }
                sortConcepts(group);
                rows.add(new Row(Kind.GROUP, domain, null));
                for (String concept : group) {
                    rows.add(new Row(Kind.CONCEPT, domain, concept));
                }
            }
        } else {
            List<String> group = new ArrayList<>(visible);
            sortConcepts(group);
            for (String concept : group) {
                rows.add(new Row(Kind.CONCEPT, selectedTab, concept));
            }
        }

        if (selected == null || !visible.contains(selected)) {
            selected = visible.isEmpty() ? null : visible.get(0);
        }
        hovered = null;
        if (resetScroll) {
            scroll = 0;
            scrollTarget = 0;
        } else {
            scrollTarget = Mth.clamp(scrollTarget, 0, maxScroll());
            scroll = Mth.clamp(scroll, 0, maxScroll());
        }
    }

    /**
     * Fist rows read as a ladder, not as a dictionary.
     *
     * <p>Alphabetical order put Wooden above Netherite, which is exactly backwards from the way
     * the stages are climbed. Labor stays above the whole ladder because it is the concept that
     * unlocks it; every other concept in the domain still sorts by its display name.
     */
    private void sortConcepts(List<String> group) {
        group.sort((a, b) -> {
            int byRank = Integer.compare(menuRank(a), menuRank(b));
            return byRank != 0
                    ? byRank
                    : menuName(a).getString().compareToIgnoreCase(menuName(b).getString());
        });
    }

    private int menuRank(String concept) {
        if (Concepts.MINE_LABOR.equals(concept)) {
            return 0;
        }
        for (int tier = 0; tier < FistTiers.TIER_COUNT; tier++) {
            if (FistTiers.concept(tier).equals(concept)) {
                return 10 + FistTiers.TIER_COUNT - 1 - tier;
            }
        }
        for (int tier = 0; tier < CombatFistTiers.TIER_COUNT; tier++) {
            if (CombatFistTiers.concept(tier).equals(concept)) {
                return 10 + CombatFistTiers.TIER_COUNT - 1 - tier;
            }
        }
        return 100;
    }

    private boolean matches(String concept) {
        String query = search.strip().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return true;
        }
        if (concept.toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        if (menuName(concept).getString().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        return description(concept).getString().toLowerCase(Locale.ROOT).contains(query);
    }

    private Component menuName(String concept) {
        if (concept == null) {
            return Component.empty();
        }
        // Concepts.displayName appends " (DropRate)" to a drop concept. That suffix exists to
        // distinguish the chat line from its contact sibling; in a card whose whole right side says
        // DROP it is just a second, noisier label.
        if (concept.startsWith(Concepts.DROP_PREFIX)) {
            String contact = Concepts.CONTACT_PREFIX
                    + concept.substring(Concepts.DROP_PREFIX.length());
            return names.computeIfAbsent(contact, Concepts::displayName);
        }
        return names.computeIfAbsent(concept, Concepts::displayName);
    }

    private Component description(String concept) {
        return descriptions.computeIfAbsent(concept, key -> {
            String name = menuName(key).getString();
            String fallback;
            if (key.startsWith(Concepts.TYPE_PREFIX)) {
                fallback = tr("adaptionwheel.desc_fallback.type", name);
            } else if (key.startsWith(Concepts.CONTACT_PREFIX)) {
                fallback = tr("adaptionwheel.desc_fallback.contact", name);
            } else if (key.startsWith(Concepts.OFFENSE_PREFIX)) {
                fallback = tr("adaptionwheel.desc_fallback.offense", name);
            } else if (key.startsWith(Concepts.DROP_PREFIX)) {
                fallback = tr("adaptionwheel.desc_fallback.drop", name);
            } else if (key.startsWith(Concepts.EXISTENCE_PREFIX)) {
                fallback = tr("adaptionwheel.desc_fallback.existence", name);
            } else if (key.startsWith(Concepts.DEBUFF_PREFIX)) {
                fallback = tr("adaptionwheel.desc_fallback.debuff", name);
            } else if (key.startsWith(Concepts.FIST_PREFIX)) {
                fallback = tr("adaptionwheel.desc_fallback.fist", name);
            } else if (key.startsWith("Combat_Fist")) {
                fallback = tr("adaptionwheel.desc_fallback.combat_fist", name);
            } else {
                fallback = tr("adaptionwheel.desc_fallback.default", name);
            }
            return Component.translatableWithFallback("adaptionwheel.desc." + key, fallback);
        });
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private String wheelLabel(ResourceLocation fontId, int maxWidth) {
        String full = WheelUi.upper(tr("adaptionwheel.gui.menu_wheel_name"));
        if (font.width(WheelUi.mono(fontId, full)) <= maxWidth) {
            return full;
        }
        return WheelUi.upper(tr("adaptionwheel.gui.menu_wheel_short"));
    }

    private ItemStack icon(String concept) {
        return icons.computeIfAbsent(concept, ConceptIcons::icon);
    }

    private ItemStack wheelIcon() {
        if (wheelIcon == null) {
            wheelIcon = new ItemStack(ModItems.MAHORAGA_WHEEL.get());
        }
        return wheelIcon;
    }

    private AdaptionTask taskFor(String concept) {
        if (concept == null) {
            return null;
        }
        for (AdaptionTask task : ClientAdaption.TASKS) {
            if (task != null && concept.equals(task.concept)) {
                return task;
            }
        }
        return null;
    }

    private boolean acquired(String concept) {
        return ClientAdaption.isAdapted(concept) || ClientAdaption.level(concept) > 0;
    }

    private boolean canToggle(String concept) {
        return concept != null && acquired(concept);
    }

    private int acquiredCount() {
        int count = 0;
        for (String concept : known) {
            if (acquired(concept)) {
                count++;
            }
        }
        return count;
    }

    private int activeCount() {
        int count = 0;
        for (String concept : known) {
            if (acquired(concept) && ClientAdaption.isEnabled(concept)) {
                count++;
            }
        }
        return count;
    }

    private int visibleConceptCount() {
        int count = 0;
        for (Row row : rows) {
            if (row.kind == Kind.CONCEPT) {
                count++;
            }
        }
        return count;
    }

    private String focusConcept() {
        if (hovered != null) {
            return hovered;
        }
        return selected;
    }

    // ---------------------------------------------------------------- layout

    private void layout() {
        margin = Mth.clamp(width / 24, 24, 48);
        footerY = height - 22;

        int searchW = Mth.clamp(width / 5, 120, 220);
        int searchX = width - margin - searchW;
        String fullTitle = WheelUi.upper(tr("adaptionwheel.gui.menu_title"));
        int halfLimit = Math.min(width / 2 - 18, searchX - 14 - width / 2);
        int maxTitleW = Math.max(120, halfLimit * 2);

        titleFont = WheelUi.MONO_30;
        titleSize = 30;
        titleTracking = 7.0f;
        if (WheelUi.trackedWidth(font, titleFont, fullTitle, titleTracking) > maxTitleW) {
            titleFont = WheelUi.MONO_24;
            titleSize = 24;
            titleTracking = 5.6f;
        }
        if (WheelUi.trackedWidth(font, titleFont, fullTitle, titleTracking) > maxTitleW) {
            titleFont = WheelUi.MONO_20;
            titleSize = 20;
            titleTracking = 4.6f;
        }
        if (WheelUi.trackedWidth(font, titleFont, fullTitle, titleTracking) > maxTitleW) {
            titleFont = WheelUi.MONO_14;
            titleSize = 14;
            titleTracking = 3.2f;
        }
        if (WheelUi.trackedWidth(font, titleFont, fullTitle, titleTracking) > maxTitleW) {
            titleFont = WheelUi.MONO_11;
            titleSize = 11;
            titleTracking = 2.4f;
        }
        titleText = WheelUi.fit(font, titleFont, fullTitle, maxTitleW);
        titleWidth = WheelUi.trackedWidth(font, titleFont, titleText, titleTracking);

        titleY = Math.max(20, height / 18);
        ruleY = titleY + titleSize + 10;
        contentTop = Math.max(ruleY + 24, (int) (height * 0.19f));
        searchRect = new Rect(searchX, titleY + titleSize / 2 - 10, searchW, 20);
        clearRect = Rect.EMPTY;

        int tabWanted = Mth.clamp(width / 6, 72, 104);
        twoColumn = width >= 460 && height >= 245;
        compactLeft = false;
        if (twoColumn) {
            leftW = Mth.clamp((int) (width * 0.29f), 140, 286);
            leftH = Math.min(360, footerY - contentTop - 18);
            if (leftH < 140) {
                twoColumn = false;
            }
        }

        if (twoColumn) {
            if (width >= 900) {
                leftX = Math.max(margin, (int) (width * 0.08f));
                leftY = contentTop + Math.max(0, (footerY - contentTop - leftH) / 2);
                tabX = leftX + leftW + Mth.clamp(width / 40, 16, 28);
                tabW = tabWanted;
                listX = tabX + tabW + 14;
                listW = Math.min(480, width - margin - listX);
                if (listW < 300) {
                    twoColumn = false;
                }
            } else {
                int gap = Mth.clamp(width / 28, 14, 40);
                int available = width - 2 * margin;
                listW = Math.min(480, available - leftW - gap - tabWanted - TAB_GAP);
                if (listW < 250) {
                    twoColumn = false;
                } else {
                    int groupW = leftW + gap + tabWanted + TAB_GAP + listW;
                    leftX = Math.max(margin, (width - groupW) / 2);
                    leftY = contentTop + Math.max(0, (footerY - contentTop - leftH) / 2);
                    tabX = leftX + leftW + gap;
                    tabW = tabWanted;
                    listX = tabX + tabW + TAB_GAP;
                }
            }
        }
        if (twoColumn) {
            compactLeft = leftH < 330;
        }

        if (!twoColumn) {
            leftX = 0;
            leftY = 0;
            leftW = 0;
            leftH = 0;
            compactLeft = false;
            tabX = margin;
            tabW = tabWanted;
            listX = tabX + tabW + TAB_GAP;
            listW = Math.min(620, width - margin - listX);
            if (listW < 220 && tabW > 60) {
                tabW = 60;
                listX = tabX + tabW + TAB_GAP;
                listW = Math.min(620, width - margin - listX);
            }
        }

        listTop = contentTop;
        listBottom = footerY - 12;
        tabY = contentTop;
        tabViewportH = Math.max(0, listBottom - tabY);
        tabScroll = Mth.clamp(tabScroll, 0, maxTabScroll());

        synergyStripH = activeSynergies.isEmpty() ? 0 : SYNERGY_HEIGHT + 6;
        synergyTop = listTop;
        abilityTop = listTop + synergyStripH;

        List<ActiveAbility> unlockedAbilities = AbilityAvailability.unlocked();
        int abilityRows = Math.min(2, unlockedAbilities.size());
        abilityAreaH = unlockedAbilities.isEmpty()
                ? (height < 300 ? 48 : ABILITY_HEIGHT)
                : Math.max(1, abilityRows) * ABILITY_ROW_HEIGHT
                        + Math.max(0, abilityRows - 1) * ABILITY_ROW_GAP;
        placeholderRect = new Rect(listX, abilityTop, listW, abilityAreaH);

        adaptLabelY = abilityTop + abilityAreaH + 10;
        scrollViewportTop = adaptLabelY + 18;
        scrollViewportBottom = listBottom;

        scrollTarget = Mth.clamp(scrollTarget, 0, maxScroll());
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private int maxScroll() {
        int viewport = scrollViewportBottom - scrollViewportTop;
        return Math.max(0, contentHeight() - Math.max(0, viewport));
    }

    private int maxTabScroll() {
        int total = (availableDomains.size() + 1) * TAB_HEIGHT;
        return Math.max(0, total - Math.max(0, tabViewportH));
    }

    private int contentHeight() {
        int total = 0;
        for (Row row : rows) {
            total += row.kind == Kind.GROUP ? GROUP_HEIGHT : CARD_STRIDE;
        }
        return Math.max(0, total - (rows.isEmpty() ? 0 : CARD_GAP));
    }

    private void updateScrollAnimation() {
        scrollTarget = Mth.clamp(scrollTarget, 0, maxScroll());
        double delta = scrollTarget - scroll;
        if (Math.abs(delta) < 0.4) {
            scroll = scrollTarget;
        } else {
            scroll += delta * 0.28;
        }
    }

    private void updateHover(int mouseX, int mouseY) {
        hovered = null;
        if (mouseX < listX || mouseX >= listX + listW
                || mouseY < scrollViewportTop || mouseY >= scrollViewportBottom) {
            return;
        }
        int y = scrollViewportTop - (int) Math.round(scroll);
        for (Row row : rows) {
            if (row.kind == Kind.GROUP) {
                y += GROUP_HEIGHT;
            } else {
                if (mouseY >= y && mouseY < y + CARD_HEIGHT) {
                    hovered = row.concept;
                    return;
                }
                y += CARD_STRIDE;
            }
        }
    }

    // ---------------------------------------------------------------- drawing

    private void drawHeader(GuiGraphics graphics) {
        long now = Util.getMillis();
        float t = Mth.clamp((now - openedAt) / 400f, 0f, 1f);
        float appear = 1f - (1f - t) * (1f - t) * (1f - t);

        int eyebrowY = titleY - 12;
        if (eyebrowY >= 2) {
            String eyebrow = WheelUi.upper(tr("adaptionwheel.gui.menu_eyebrow"));
            int eyebrowWidth = WheelUi.trackedWidth(font, WheelUi.MONO_8, eyebrow, 3f);
            int eyebrowX = width / 2 - eyebrowWidth / 2;
            WheelUi.trackedCentered(graphics, font, WheelUi.MONO_8, eyebrow, width / 2, eyebrowY,
                    WheelUi.withAlpha(MUTED, appear * 0.9f), 3f);
            graphics.fill(eyebrowX - 20, eyebrowY + 3, eyebrowX - 10, eyebrowY + 4,
                    WheelUi.withAlpha(GOLD, appear * 0.55f));
            graphics.fill(eyebrowX + eyebrowWidth + 10, eyebrowY + 3,
                    eyebrowX + eyebrowWidth + 20, eyebrowY + 4,
                    WheelUi.withAlpha(GOLD, appear * 0.55f));
        }

        int titleDrawY = titleY + Math.round((1f - appear) * 3f);
        WheelUi.trackedCentered(graphics, font, titleFont, titleText, width / 2, titleDrawY,
                WheelUi.withAlpha(INK, 0.35f + 0.65f * appear), titleTracking);

        int ruleWidth = Math.min(titleWidth + 90, width - 2 * margin);
        int x0 = width / 2 - ruleWidth / 2;
        int segs = 80;
        int dip = 6;
        for (int i = 0; i < segs; i++) {
            float t0 = i / (float) segs;
            float t1 = (i + 1) / (float) segs;
            float mid = (t0 + t1) * 0.5f;
            int sx = x0 + Math.round(t0 * ruleWidth);
            int ex = x0 + Math.round(t1 * ruleWidth);
            int yy = ruleY + Math.round((1f - appear) * 2f) + Math.round(dip * (1f - (2f * mid - 1f) * (2f * mid - 1f)));
            float edge = Math.min(1f, Math.min(t0 * 14f, (1f - t0) * 14f));
            int color = WheelUi.withAlpha(INK, 0.28f * edge * appear);
            graphics.fill(sx, yy, Math.max(sx + 1, ex), yy + 1, color);
            graphics.fill(sx, yy + 1, Math.max(sx + 1, ex), yy + 2,
                    WheelUi.withAlpha(INK, 0.10f * edge * appear));
        }
        graphics.fill(x0, ruleY - 4, x0 + 1, ruleY + 1, WheelUi.withAlpha(INK, 0.20f * appear));
        graphics.fill(x0 + ruleWidth - 1, ruleY - 4, x0 + ruleWidth, ruleY + 1,
                WheelUi.withAlpha(INK, 0.20f * appear));
    }

    private void drawSearch(GuiGraphics graphics) {
        if (searchRect.w <= 0) {
            return;
        }
        int x = searchRect.x;
        int y = searchRect.y;
        int w = searchRect.w;
        int h = searchRect.h;
        int color = searchFocused ? INK_SOFT : MUTED;

        WheelUi.outlineRounded(graphics, x, y, w, h, 6,
                searchFocused ? WheelUi.withAlpha(GOLD, 0.80f) : WheelUi.BORDER, 0xFF0D0D0F);
        graphics.fill(x + 6, y + 1, x + w - 6, y + 2, 0x14FFFFFF);

        WheelUi.ringCircle(graphics, x + 9, y + 10, 4, color);
        graphics.fill(x + 12, y + 13, x + 15, y + 14, color);

        int textX = x + 19;
        int maxW = w - 38;
        int caret = Mth.clamp(searchCaret, 0, search.length());
        if (search.isEmpty()) {
            WheelUi.text(graphics, font, WheelUi.MONO_8, tr("adaptionwheel.gui.menu_search"),
                    textX, y + 6, FAINT, false);
        } else {
            searchStart = 0;
            if (searchFocused) {
                while (searchStart < caret
                        && font.width(WheelUi.mono(WheelUi.MONO_8,
                                search.substring(searchStart, caret))) > maxW - 4) {
                    searchStart++;
                }
            }
            String shown = search.substring(searchStart);
            if (!searchFocused) {
                shown = WheelUi.fit(font, WheelUi.MONO_8, shown, maxW);
            }
            while (shown.length() > 1 && font.width(WheelUi.mono(WheelUi.MONO_8, shown)) > maxW) {
                shown = shown.substring(0, shown.length() - 1);
            }
            WheelUi.text(graphics, font, WheelUi.MONO_8, shown, textX, y + 6, INK, false);
        }

        if (searchFocused && (Util.getMillis() / 500L) % 2L == 0L) {
            int caretX = textX + font.width(WheelUi.mono(WheelUi.MONO_8,
                    search.substring(searchStart, Math.max(searchStart, caret))));
            caretX = Math.min(caretX, x + w - 20);
            graphics.fill(caretX, y + 6, caretX + 1, y + 16, INK);
        }

        clearRect = Rect.EMPTY;
        if (!search.isEmpty()) {
            clearRect = new Rect(x + w - 17, y + 4, 12, 12);
            int clearColor = clearRect.contains(lastMouseX, lastMouseY) ? INK_SOFT : FAINT;
            WheelUi.centered(graphics, font, WheelUi.MONO_8, "\u00D7",
                    clearRect.x + clearRect.w / 2, y + 6, clearColor, false);
        }
    }

    private void drawLeftCard(GuiGraphics graphics) {
        leftToggle = Rect.EMPTY;
        if (leftH < 190) {
            drawMiniLeftCard(graphics);
            return;
        }
        if (compactLeft) {
            drawCompactLeftCard(graphics);
            return;
        }
        int x = leftX;
        int y = leftY;
        int w = leftW;
        int h = leftH;

        WheelUi.card(graphics, x, y, w, h, 12, CARD, WheelUi.BORDER);

        int lineX = x + 18;
        int lineRight = x + w - 18;
        int segments = 36;
        for (int i = 0; i < segments; i++) {
            int sx = lineX + (lineRight - lineX) * i / segments;
            int ex = lineX + (lineRight - lineX) * (i + 1) / segments;
            int color = WheelUi.mix(GOLD, ROSE, i / (float) (segments - 1));
            graphics.fill(sx, y + 1, Math.max(sx + 1, ex), y + 3, color);
        }

        int cx = x + w / 2;
        int cy = y + 62;
        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy + 32, 0);
        graphics.pose().scale(1f, 0.20f, 1f);
        WheelUi.fillCircle(graphics, 0, 0, 22, 0x66000000);
        graphics.pose().popPose();

        WheelUi.fillCircle(graphics, cx, cy, 34, WheelUi.tint(GOLD));
        WheelUi.fillCircle(graphics, cx, cy, 32, 0xFF1B1B1E);
        WheelUi.ringCircle(graphics, cx, cy, 32, 0x30FFFFFF);
        WheelUi.ringCircle(graphics, cx, cy, 34, WheelUi.withAlpha(GOLD, 0.38f));
        WheelUi.itemIcon(graphics, wheelIcon(), cx - 20, cy - 20, 40);

        WheelUi.centered(graphics, font, WheelUi.MONO_11,
                WheelUi.fit(font, WheelUi.MONO_11,
                        wheelLabel(WheelUi.MONO_11, w - 32), w - 32),
                cx, y + 104, INK, false);
        centeredWrapped(graphics, Component.translatable("adaptionwheel.gui.menu_wheel_desc"),
                cx, y + 120, w - 44, 10, 2, MUTED);

        String equipped = tr(ClientAdaption.wearingWheel
                ? "adaptionwheel.gui.menu_equipped"
                : "adaptionwheel.gui.menu_not_equipped");
        int pillTextW = font.width(WheelUi.mono(WheelUi.MONO_8, equipped));
        int pillW = pillTextW + 24;
        int pillX = cx - pillW / 2;
        int pillY = y + 148;
        WheelUi.fillRounded(graphics, pillX, pillY, pillW, 17, 8,
                ClientAdaption.wearingWheel ? GREEN_BG : 0xFF202024);
        WheelUi.fillCircle(graphics, pillX + 9, pillY + 8, 2,
                ClientAdaption.wearingWheel ? GREEN : FAINT);
        WheelUi.centered(graphics, font, WheelUi.MONO_8, equipped, cx + 3, pillY + 4,
                ClientAdaption.wearingWheel ? GREEN : MUTED, false);

        int statsY = y + 174;
        WheelUi.centered(graphics, font, WheelUi.MONO_20, String.valueOf(acquiredCount()),
                cx - 40, statsY, INK, false);
        WheelUi.centered(graphics, font, WheelUi.MONO_8,
                WheelUi.upper(tr("adaptionwheel.gui.menu_adaptations")),
                cx - 40, statsY + 22, MUTED, false);
        WheelUi.centered(graphics, font, WheelUi.MONO_20, String.valueOf(activeCount()),
                cx + 40, statsY, INK, false);
        WheelUi.centered(graphics, font, WheelUi.MONO_8,
                WheelUi.upper(tr("adaptionwheel.gui.menu_active")),
                cx + 40, statsY + 22, MUTED, false);
        graphics.fill(cx, statsY + 2, cx + 1, statsY + 26, 0x24FFFFFF);

        graphics.fill(x + 20, y + 216, x + w - 20, y + 217, HAIRLINE);
        WheelUi.trackedLeft(graphics, font, WheelUi.MONO_8,
                WheelUi.upper(tr("adaptionwheel.gui.menu_selected")),
                x + 20, y + 226, MUTED, 1.5f);

        String focus = focusConcept();
        if (focus == null) {
            WheelUi.text(graphics, font, WheelUi.MONO_11,
                    tr("adaptionwheel.gui.menu_no_selection"), x + 20, y + 246, INK_SOFT, false);
            WheelUi.wrapped(graphics, font, WheelUi.MONO_8,
                    Component.translatable("adaptionwheel.gui.menu_no_selection_hint"),
                    x + 20, y + 264, w - 40, 10, 3, MUTED);
            return;
        }

        int accent = safeColor(Concepts.color(focus));
        boolean enabled = ClientAdaption.isEnabled(focus);
        int effective = enabled ? accent : WheelUi.disabled(accent);
        AdaptionTask task = taskFor(focus);

        WheelUi.fillRounded(graphics, x + 16, y + 242, 28, 28, 7,
                WheelUi.tint(effective));
        WheelUi.itemIcon(graphics, icon(focus), x + 19, y + 245, 22);
        WheelUi.text(graphics, font, WheelUi.MONO_11,
                WheelUi.fit(font, WheelUi.MONO_11, WheelUi.upper(menuName(focus).getString()), w - 72),
                x + 50, y + 241, enabled ? INK : FAINT, false);
        String subtitle = WheelUi.upper(AdaptationRegistry.domainOf(focus).translation().getString())
                + "  \u00B7  " + stateText(focus, task);
        WheelUi.text(graphics, font, WheelUi.MONO_8,
                WheelUi.fit(font, WheelUi.MONO_8, subtitle, w - 72),
                x + 50, y + 257, task != null ? WheelUi.AMBER : (enabled ? MUTED : FAINT), false);

        WheelUi.wrapped(graphics, font, WheelUi.MONO_8, description(focus),
                x + 20, y + 276, w - 40, 10, h >= 350 ? 3 : 2, MUTED);

        int toggleY = y + h - 38;
        if (canToggle(focus)) {
            WheelUi.text(graphics, font, WheelUi.MONO_8,
                    WheelUi.upper(tr(enabled
                            ? "adaptionwheel.gui.menu_enabled"
                            : "adaptionwheel.gui.menu_paused")),
                    x + 20, toggleY + 4, enabled ? INK_SOFT : MUTED, false);
            String onOff = tr(enabled ? "adaptionwheel.gui.menu_on" : "adaptionwheel.gui.menu_off");
            int onOffW = font.width(WheelUi.mono(WheelUi.MONO_8, onOff));
            int switchX = x + w - 58;
            WheelUi.text(graphics, font, WheelUi.MONO_8, onOff,
                    switchX - 8 - onOffW, toggleY + 4, enabled ? INK_SOFT : FAINT, false);
            boolean switchHovered = lastMouseX >= switchX - 2 && lastMouseX < switchX + 36
                    && lastMouseY >= toggleY - 2 && lastMouseY < toggleY + 20;
            WheelUi.toggle(graphics, switchX, toggleY, enabled, switchHovered);
            leftToggle = new Rect(switchX - 26, toggleY - 4, 64, 24);
        } else {
            WheelUi.text(graphics, font, WheelUi.MONO_8,
                    WheelUi.upper(tr("adaptionwheel.gui.menu_analyzing")),
                    x + 20, toggleY + 4, WheelUi.AMBER, false);
            float progress = task != null ? ClientAdaption.taskProgress(task) : 0f;
            int barW = w - 40;
            graphics.fill(x + 20, toggleY + 13, x + 20 + barW, toggleY + 14, HAIRLINE);
            graphics.fill(x + 20, toggleY + 13,
                    x + 20 + Math.round(barW * Mth.clamp(progress, 0f, 1f)), toggleY + 14,
                    WheelUi.AMBER);
        }
    }

    private void drawCompactLeftCard(GuiGraphics graphics) {
        int x = leftX;
        int y = leftY;
        int w = leftW;
        int h = leftH;

        WheelUi.card(graphics, x, y, w, h, 12, CARD, WheelUi.BORDER);

        int lineX = x + 12;
        int lineRight = x + w - 12;
        int segments = 24;
        for (int i = 0; i < segments; i++) {
            int sx = lineX + (lineRight - lineX) * i / segments;
            int ex = lineX + (lineRight - lineX) * (i + 1) / segments;
            graphics.fill(sx, y + 1, Math.max(sx + 1, ex), y + 3,
                    WheelUi.mix(GOLD, ROSE, i / (float) (segments - 1)));
        }

        WheelUi.fillRounded(graphics, x + 12, y + 12, 26, 26, 6,
                WheelUi.tint(GOLD));
        WheelUi.itemIcon(graphics, wheelIcon(), x + 15, y + 15, 20);
        WheelUi.text(graphics, font, WheelUi.MONO_9,
                WheelUi.fit(font, WheelUi.MONO_9,
                        wheelLabel(WheelUi.MONO_9, w - 54), w - 54),
                x + 44, y + 12, INK, false);

        String equipped = tr(ClientAdaption.wearingWheel
                ? "adaptionwheel.gui.menu_equipped"
                : "adaptionwheel.gui.menu_not_equipped");
        int pillW = font.width(WheelUi.mono(WheelUi.MONO_8, equipped)) + 18;
        int pillX = x + 12;
        int pillY = y + 44;
        WheelUi.fillRounded(graphics, pillX, pillY, pillW, 14, 7,
                ClientAdaption.wearingWheel ? GREEN_BG : 0xFF202024);
        WheelUi.fillCircle(graphics, pillX + 7, pillY + 7, 2,
                ClientAdaption.wearingWheel ? GREEN : FAINT);
        WheelUi.text(graphics, font, WheelUi.MONO_8, equipped,
                pillX + 12, pillY + 3, ClientAdaption.wearingWheel ? GREEN : MUTED, false);

        int statsY = y + 66;
        int half = w / 2;
        WheelUi.centered(graphics, font, WheelUi.MONO_14, String.valueOf(acquiredCount()),
                x + half / 2, statsY, INK, false);
        WheelUi.centered(graphics, font, WheelUi.MONO_8,
                WheelUi.upper(tr("adaptionwheel.gui.menu_adaptations")),
                x + half / 2, statsY + 17, MUTED, false);
        WheelUi.centered(graphics, font, WheelUi.MONO_14, String.valueOf(activeCount()),
                x + half + half / 2, statsY, INK, false);
        WheelUi.centered(graphics, font, WheelUi.MONO_8,
                WheelUi.upper(tr("adaptionwheel.gui.menu_active")),
                x + half + half / 2, statsY + 17, MUTED, false);
        graphics.fill(x + half, statsY + 1, x + half + 1, statsY + 21, 0x24FFFFFF);

        graphics.fill(x + 12, y + 94, x + w - 12, y + 95, HAIRLINE);
        WheelUi.trackedLeft(graphics, font, WheelUi.MONO_8,
                WheelUi.upper(tr("adaptionwheel.gui.menu_selected")),
                x + 12, y + 101, MUTED, 1.2f);

        String focus = focusConcept();
        if (focus == null) {
            WheelUi.text(graphics, font, WheelUi.MONO_9,
                    tr("adaptionwheel.gui.menu_no_selection"), x + 12, y + 119, INK_SOFT, false);
            WheelUi.wrapped(graphics, font, WheelUi.MONO_8,
                    Component.translatable("adaptionwheel.gui.menu_no_selection_hint"),
                    x + 12, y + 135, w - 24, 9, 2, MUTED);
            return;
        }

        int accent = safeColor(Concepts.color(focus));
        boolean enabled = ClientAdaption.isEnabled(focus);
        int effective = enabled ? accent : WheelUi.disabled(accent);
        AdaptionTask task = taskFor(focus);

        WheelUi.fillRounded(graphics, x + 12, y + 118, 20, 20, 5,
                WheelUi.tint(effective));
        WheelUi.itemIcon(graphics, icon(focus), x + 14, y + 120, 16);
        WheelUi.text(graphics, font, WheelUi.MONO_9,
                WheelUi.fit(font, WheelUi.MONO_9,
                        WheelUi.upper(menuName(focus).getString()), w - 48),
                x + 36, y + 115, enabled ? INK : FAINT, false);
        String subtitle = WheelUi.upper(AdaptationRegistry.domainOf(focus).translation().getString())
                + "  \u00B7  " + stateText(focus, task);
        WheelUi.text(graphics, font, WheelUi.MONO_8,
                WheelUi.fit(font, WheelUi.MONO_8, subtitle, w - 48),
                x + 36, y + 128, task != null ? WheelUi.AMBER : (enabled ? MUTED : FAINT), false);

        WheelUi.wrapped(graphics, font, WheelUi.MONO_8, description(focus),
                x + 12, y + 146, w - 24, 9, h >= 215 ? 2 : 1, MUTED);

        int toggleY = y + h - 24;
        if (canToggle(focus)) {
            WheelUi.text(graphics, font, WheelUi.MONO_8,
                    WheelUi.upper(tr(enabled
                            ? "adaptionwheel.gui.menu_enabled"
                            : "adaptionwheel.gui.menu_paused")),
                    x + 12, toggleY + 4, enabled ? INK_SOFT : MUTED, false);
            String onOff = tr(enabled ? "adaptionwheel.gui.menu_on" : "adaptionwheel.gui.menu_off");
            int onOffW = font.width(WheelUi.mono(WheelUi.MONO_8, onOff));
            int switchX = x + w - 42;
            WheelUi.text(graphics, font, WheelUi.MONO_8, onOff,
                    switchX - 6 - onOffW, toggleY + 4, enabled ? INK_SOFT : FAINT, false);
            boolean switchHovered = lastMouseX >= switchX - 2 && lastMouseX < switchX + 34
                    && lastMouseY >= toggleY - 2 && lastMouseY < toggleY + 20;
            WheelUi.toggle(graphics, switchX, toggleY, enabled, switchHovered);
            leftToggle = new Rect(switchX - 24, toggleY - 4, 58, 24);
        } else {
            WheelUi.text(graphics, font, WheelUi.MONO_8,
                    WheelUi.upper(tr("adaptionwheel.gui.menu_analyzing")),
                    x + 12, toggleY + 4, WheelUi.AMBER, false);
            float progress = task != null ? ClientAdaption.taskProgress(task) : 0f;
            int barW = w - 24;
            graphics.fill(x + 12, toggleY + 13, x + 12 + barW, toggleY + 14, HAIRLINE);
            graphics.fill(x + 12, toggleY + 13,
                    x + 12 + Math.round(barW * Mth.clamp(progress, 0f, 1f)), toggleY + 14,
                    WheelUi.AMBER);
        }
    }

    private void drawMiniLeftCard(GuiGraphics graphics) {
        int x = leftX;
        int y = leftY;
        int w = leftW;
        int h = leftH;

        WheelUi.card(graphics, x, y, w, h, 10, CARD, WheelUi.BORDER);

        int lineX = x + 10;
        int lineRight = x + w - 10;
        int segments = 16;
        for (int i = 0; i < segments; i++) {
            int sx = lineX + (lineRight - lineX) * i / segments;
            int ex = lineX + (lineRight - lineX) * (i + 1) / segments;
            graphics.fill(sx, y + 1, Math.max(sx + 1, ex), y + 3,
                    WheelUi.mix(GOLD, ROSE, i / (float) (segments - 1)));
        }

        WheelUi.fillRounded(graphics, x + 10, y + 9, 22, 22, 5,
                WheelUi.tint(GOLD));
        WheelUi.itemIcon(graphics, wheelIcon(), x + 13, y + 12, 16);
        WheelUi.text(graphics, font, WheelUi.MONO_8,
                WheelUi.fit(font, WheelUi.MONO_8,
                        wheelLabel(WheelUi.MONO_8, w - 46), w - 46),
                x + 38, y + 9, INK, false);

        String equipped = tr(ClientAdaption.wearingWheel
                ? "adaptionwheel.gui.menu_equipped"
                : "adaptionwheel.gui.menu_not_equipped");
        int pillW = font.width(WheelUi.mono(WheelUi.MONO_8, equipped)) + 16;
        int pillX = x + 10;
        int pillY = y + 34;
        WheelUi.fillRounded(graphics, pillX, pillY, pillW, 13, 6,
                ClientAdaption.wearingWheel ? GREEN_BG : 0xFF202024);
        WheelUi.fillCircle(graphics, pillX + 6, pillY + 6, 2,
                ClientAdaption.wearingWheel ? GREEN : FAINT);
        WheelUi.text(graphics, font, WheelUi.MONO_8, equipped,
                pillX + 11, pillY + 3, ClientAdaption.wearingWheel ? GREEN : MUTED, false);

        int statsY = y + 51;
        int half = w / 2;
        WheelUi.centered(graphics, font, WheelUi.MONO_11, String.valueOf(acquiredCount()),
                x + half / 2, statsY, INK, false);
        WheelUi.centered(graphics, font, WheelUi.MONO_8,
                WheelUi.upper(tr("adaptionwheel.gui.menu_adaptations")),
                x + half / 2, statsY + 13, MUTED, false);
        WheelUi.centered(graphics, font, WheelUi.MONO_11, String.valueOf(activeCount()),
                x + half + half / 2, statsY, INK, false);
        WheelUi.centered(graphics, font, WheelUi.MONO_8,
                WheelUi.upper(tr("adaptionwheel.gui.menu_active")),
                x + half + half / 2, statsY + 13, MUTED, false);
        graphics.fill(x + half, statsY + 1, x + half + 1, statsY + 21, 0x24FFFFFF);

        graphics.fill(x + 10, y + 78, x + w - 10, y + 79, HAIRLINE);
        String focus = focusConcept();

        int toggleY = y + h - 22;
        int regionTop = y + 84;
        int regionBottom = toggleY - 4;
        int blockTop = regionTop + Math.max(0, (regionBottom - regionTop - 34) / 2);

        if (focus == null) {
            WheelUi.text(graphics, font, WheelUi.MONO_8,
                    WheelUi.fit(font, WheelUi.MONO_8,
                            tr("adaptionwheel.gui.menu_no_selection"), w - 20),
                    x + 10, blockTop, INK_SOFT, false);
            WheelUi.text(graphics, font, WheelUi.MONO_8,
                    WheelUi.fit(font, WheelUi.MONO_8,
                            tr("adaptionwheel.gui.menu_no_selection_hint"), w - 20),
                    x + 10, blockTop + 12, MUTED, false);
            return;
        }

        int accent = safeColor(Concepts.color(focus));
        boolean enabled = ClientAdaption.isEnabled(focus);
        int effective = enabled ? accent : WheelUi.disabled(accent);
        AdaptionTask task = taskFor(focus);

        WheelUi.fillRounded(graphics, x + 10, blockTop, 18, 18, 4,
                WheelUi.tint(effective));
        WheelUi.itemIcon(graphics, icon(focus), x + 12, blockTop + 2, 14);
        WheelUi.text(graphics, font, WheelUi.MONO_8,
                WheelUi.fit(font, WheelUi.MONO_8,
                        WheelUi.upper(menuName(focus).getString()), w - 38),
                x + 32, blockTop - 1, enabled ? INK : FAINT, false);
        String subtitle = WheelUi.upper(AdaptationRegistry.domainOf(focus).translation().getString())
                + "  \u00B7  " + stateText(focus, task);
        WheelUi.text(graphics, font, WheelUi.MONO_8,
                WheelUi.fit(font, WheelUi.MONO_8, subtitle, w - 38),
                x + 32, blockTop + 10, task != null ? WheelUi.AMBER : (enabled ? MUTED : FAINT), false);
        WheelUi.text(graphics, font, WheelUi.MONO_8,
                WheelUi.fit(font, WheelUi.MONO_8, description(focus).getString(), w - 20),
                x + 10, blockTop + 24, MUTED, false);

        if (canToggle(focus)) {
            WheelUi.text(graphics, font, WheelUi.MONO_8,
                    WheelUi.upper(tr(enabled
                            ? "adaptionwheel.gui.menu_enabled"
                            : "adaptionwheel.gui.menu_paused")),
                    x + 10, toggleY + 4, enabled ? INK_SOFT : MUTED, false);
            String onOff = tr(enabled ? "adaptionwheel.gui.menu_on" : "adaptionwheel.gui.menu_off");
            int onOffW = font.width(WheelUi.mono(WheelUi.MONO_8, onOff));
            int switchX = x + w - 42;
            WheelUi.text(graphics, font, WheelUi.MONO_8, onOff,
                    switchX - 6 - onOffW, toggleY + 4, enabled ? INK_SOFT : FAINT, false);
            boolean switchHovered = lastMouseX >= switchX - 2 && lastMouseX < switchX + 34
                    && lastMouseY >= toggleY - 2 && lastMouseY < toggleY + 20;
            WheelUi.toggle(graphics, switchX, toggleY, enabled, switchHovered);
            leftToggle = new Rect(switchX - 24, toggleY - 4, 58, 24);
        } else {
            WheelUi.text(graphics, font, WheelUi.MONO_8,
                    WheelUi.upper(tr("adaptionwheel.gui.menu_analyzing")),
                    x + 10, toggleY + 4, WheelUi.AMBER, false);
            float progress = task != null ? ClientAdaption.taskProgress(task) : 0f;
            int barW = w - 20;
            graphics.fill(x + 10, toggleY + 13, x + 10 + barW, toggleY + 14, HAIRLINE);
            graphics.fill(x + 10, toggleY + 13,
                    x + 10 + Math.round(barW * Mth.clamp(progress, 0f, 1f)), toggleY + 14,
                    WheelUi.AMBER);
        }
    }

    /**
     * The wheel is decoration, not a control. It used to sit in the middle of the gap with a
     * crosshair, which put both directly over the domain rail -- and a crosshair that does nothing
     * promises movement the panel does not have. The crosshair is gone; the wheel is pinned to the
     * bottom of the gap, below the rail, and is simply not drawn when the rail has no space left.
     */
    private void drawFloatingWheel(GuiGraphics graphics) {
        int gap = listX - (leftX + leftW);
        if (gap < 44) {
            return;
        }
        int railBottom = tabY + (availableDomains.size() + 1) * TAB_HEIGHT
                - (int) Math.round(tabScroll);
        int floatCX = (leftX + leftW + listX) / 2;
        int floatCY = footerY - 44;
        if (floatCY - 24 < railBottom + 6) {
            return;
        }
        int bob = (int) Math.round(Math.sin((Util.getMillis() - openedAt) / 700.0) * 2.0);
        graphics.pose().pushPose();
        graphics.pose().translate(floatCX, floatCY + 26, 0);
        graphics.pose().scale(1f, 0.18f, 1f);
        WheelUi.fillCircle(graphics, 0, 0, 22, 0x66000000);
        graphics.pose().popPose();
        WheelUi.itemIcon(graphics, wheelIcon(), floatCX - 20, floatCY - 20 + bob, 40);
    }

    private void drawTabs(GuiGraphics graphics) {
        tabHits.clear();
        if (tabViewportH <= 0 || tabW <= 0) {
            return;
        }
        int max = maxTabScroll();
        tabScroll = Mth.clamp(tabScroll, 0, max);

        graphics.enableScissor(tabX - 3, tabY - 2, tabX + tabW + 4, tabY + tabViewportH + 2);
        int y = tabY - (int) Math.round(tabScroll);
        int tabs = availableDomains.size() + 1;
        for (int i = 0; i < tabs; i++) {
            AdaptationDomain domain = i == 0 ? null : availableDomains.get(i - 1);
            boolean active = (selectedTab == null && domain == null)
                    || (selectedTab != null && selectedTab == domain);
            boolean hot = lastMouseX >= tabX && lastMouseX < tabX + tabW
                    && lastMouseY >= tabY && lastMouseY < tabY + tabViewportH
                    && lastMouseY >= y && lastMouseY < y + TAB_HEIGHT;
            if (y + TAB_HEIGHT > tabY - 2 && y < tabY + tabViewportH + 2) {
                if (active) {
                    WheelUi.fillRounded(graphics, tabX, y + 2, tabW, TAB_HEIGHT - 4, 5,
                            0x24FFFFFF);
                    graphics.fill(tabX, y + 4, tabX + 2, y + TAB_HEIGHT - 4, GOLD);
                } else if (hot) {
                    WheelUi.fillRounded(graphics, tabX, y + 2, tabW, TAB_HEIGHT - 4, 5,
                            0x12FFFFFF);
                }
                int textX = tabX + 10;
                if (domain != null) {
                    WheelUi.fillRounded(graphics, tabX + 8, y + TAB_HEIGHT / 2 - 2, 4, 4, 2,
                            safeColor(domain.getColor()));
                    textX = tabX + 18;
                }
                String label = WheelUi.upper(domain == null
                        ? tr("adaptionwheel.gui.tab_all")
                        : domain.translation().getString());
                WheelUi.text(graphics, font, WheelUi.MONO_8,
                        WheelUi.fit(font, WheelUi.MONO_8, label, tabX + tabW - 8 - textX),
                        textX, y + 6, active ? INK : (hot ? INK_SOFT : MUTED), false);
            }
            tabHits.add(new TabView(domain, new Rect(tabX, y, tabW, TAB_HEIGHT)));
            y += TAB_HEIGHT;
        }
        graphics.disableScissor();

        if (max > 0) {
            int trackX = tabX + tabW + 2;
            int thumbH = Math.max(16, (int) (tabViewportH
                    * (tabViewportH / (double) ((availableDomains.size() + 1) * TAB_HEIGHT))));
            thumbH = Math.min(tabViewportH, thumbH);
            int thumbY = tabY + (int) Math.round((tabScroll / (double) max)
                    * (tabViewportH - thumbH));
            graphics.fill(trackX, tabY, trackX + 1, tabY + tabViewportH, 0x1AFFFFFF);
            WheelUi.fillRounded(graphics, trackX, thumbY, 1, thumbH, 1, 0x55FFFFFF);
        }
    }

    private void drawRightColumn(GuiGraphics graphics) {
        cards.clear();
        abilityHits.clear();

        if (!activeSynergies.isEmpty()) {
            drawSynergyStrip(graphics);
        }
        drawAbilityArea(graphics);
        drawSectionLabel(graphics);

        if (rows.isEmpty()) {
            drawEmpty(graphics);
            return;
        }

        graphics.enableScissor(listX - 5, scrollViewportTop, listX + listW + 5,
                scrollViewportBottom);
        int y = scrollViewportTop - (int) Math.round(scroll);
        for (Row row : rows) {
            int rowHeight = row.kind == Kind.GROUP ? GROUP_HEIGHT : CARD_STRIDE;
            if (y + rowHeight > scrollViewportTop - 4 && y < scrollViewportBottom + 4) {
                if (row.kind == Kind.GROUP) {
                    drawGroupHeader(graphics, row.domain, listX, y, listW);
                } else {
                    drawCard(graphics, row.concept, listX, y, listW, CARD_HEIGHT);
                }
            }
            y += rowHeight;
        }
        graphics.disableScissor();

        drawFades(graphics);
        drawScrollbar(graphics);
    }

    private void drawSynergyStrip(GuiGraphics graphics) {
        int y = synergyTop;
        String label = WheelUi.upper(tr("adaptionwheel.gui.menu_synergies"));
        WheelUi.trackedLeft(graphics, font, WheelUi.MONO_8, label,
                listX, y + 3, GOLD, 1.2f);
        int labelW = WheelUi.trackedWidth(font, WheelUi.MONO_8, label, 1.2f);
        int x = listX + labelW + 10;
        int right = listX + listW;
        int shown = 0;
        for (int i = 0; i < activeSynergies.size(); i++) {
            Synergies.Synergy synergy = activeSynergies.get(i);
            String name = WheelUi.upper(Component.translatable(
                    "adaptionwheel.synergy." + synergy.id()).getString());
            int chipW = font.width(WheelUi.mono(WheelUi.MONO_8, name)) + 14;
            int reserve = i + 1 < activeSynergies.size() ? 26 : 0;
            if (x + chipW > right - reserve) {
                break;
            }
            WheelUi.fillRounded(graphics, x, y, chipW, 14, 7,
                    WheelUi.withAlpha(GOLD, 0.14f));
            WheelUi.text(graphics, font, WheelUi.MONO_8, name, x + 7, y + 3, GOLD, false);
            x += chipW + 5;
            shown++;
        }
        if (shown < activeSynergies.size()) {
            WheelUi.text(graphics, font, WheelUi.MONO_8,
                    "+" + (activeSynergies.size() - shown), x, y + 3, MUTED, false);
        }
    }

    private void drawSectionLabel(GuiGraphics graphics) {
        String label = WheelUi.upper(tr("adaptionwheel.gui.menu_adaptations"));
        WheelUi.trackedLeft(graphics, font, WheelUi.MONO_8, label, listX, adaptLabelY, MUTED, 1.6f);
        int labelWidth = WheelUi.trackedWidth(font, WheelUi.MONO_8, label, 1.6f);
        String count = String.valueOf(visibleConceptCount());
        int countWidth = font.width(WheelUi.mono(WheelUi.MONO_8, count));
        int countX = listX + listW - countWidth;
        int lineStart = listX + labelWidth + 9;
        int lineEnd = countX - 9;
        if (lineEnd > lineStart) {
            graphics.fill(lineStart, adaptLabelY + 4, lineEnd, adaptLabelY + 5, HAIRLINE);
        }
        WheelUi.text(graphics, font, WheelUi.MONO_8, count, countX, adaptLabelY, FAINT, false);
    }

    private void drawAbilityArea(GuiGraphics graphics) {
        List<ActiveAbility> unlocked = AbilityAvailability.unlocked();
        if (unlocked.isEmpty()) {
            drawAbilityPlaceholder(graphics);
            return;
        }
        int shown = Math.min(2, unlocked.size());
        int y = abilityTop;
        for (int i = 0; i < shown; i++) {
            ActiveAbility ability = unlocked.get(i);
            drawAbilityCard(graphics, ability, listX, y, listW, ABILITY_ROW_HEIGHT);
            abilityHits.add(new AbilityHit(ability.id(), new Rect(listX, y, listW, ABILITY_ROW_HEIGHT)));
            y += ABILITY_ROW_HEIGHT + ABILITY_ROW_GAP;
        }
    }

    private void drawAbilityPlaceholder(GuiGraphics graphics) {
        int x = listX;
        int y = abilityTop;
        int w = listW;
        int h = Math.max(24, abilityAreaH);
        if (h < 56) {
            drawShortAbilityPlaceholder(graphics, x, y, w, h);
            return;
        }

        WheelUi.card(graphics, x, y, w, h, 9, CARD_ALT, WheelUi.BORDER);
        WheelUi.dashedVertical(graphics, x + 4, y + 14, h - 28, WheelUi.withAlpha(ROSE, 0.40f));

        WheelUi.fillRounded(graphics, x + 14, y + 15, 34, 34, 8, ROSE_BG);
        int plusX = x + 31;
        int plusY = y + 32;
        graphics.fill(plusX - 7, plusY, plusX + 8, plusY + 1,
                WheelUi.withAlpha(ROSE, 0.55f));
        graphics.fill(plusX, plusY - 7, plusX + 1, plusY + 8,
                WheelUi.withAlpha(ROSE, 0.55f));

        String badge = WheelUi.upper(tr("adaptionwheel.gui.menu_coming_soon"));
        int badgeW = font.width(WheelUi.mono(WheelUi.MONO_8, badge)) + 16;
        int badgeX = x + w - 14 - badgeW;
        int titleMax = Math.max(40, badgeX - (x + 58) - 10);

        WheelUi.text(graphics, font, WheelUi.MONO_11,
                WheelUi.fit(font, WheelUi.MONO_11,
                        WheelUi.upper(tr("adaptionwheel.gui.menu_abilities")), titleMax),
                x + 58, y + 13, INK, false);
        WheelUi.text(graphics, font, WheelUi.MONO_8,
                WheelUi.fit(font, WheelUi.MONO_8, tr("adaptionwheel.gui.menu_abilities_hint"),
                        w - 72),
                x + 58, y + 29, MUTED, false);

        WheelUi.fillRounded(graphics, badgeX, y + 13, badgeW, 16, 8, ROSE_BG);
        WheelUi.centered(graphics, font, WheelUi.MONO_8, badge, badgeX + badgeW / 2,
                y + 17, ROSE, false);
    }

    private void drawShortAbilityPlaceholder(GuiGraphics graphics, int x, int y, int w, int h) {
        WheelUi.card(graphics, x, y, w, h, 8, CARD_ALT, WheelUi.BORDER);
        WheelUi.dashedVertical(graphics, x + 4, y + 8, h - 16, WheelUi.withAlpha(ROSE, 0.40f));

        WheelUi.fillRounded(graphics, x + 10, y + 13, 22, 22, 5, ROSE_BG);
        int plusX = x + 21;
        int plusY = y + 24;
        graphics.fill(plusX - 5, plusY, plusX + 6, plusY + 1,
                WheelUi.withAlpha(ROSE, 0.55f));
        graphics.fill(plusX, plusY - 5, plusX + 1, plusY + 6,
                WheelUi.withAlpha(ROSE, 0.55f));

        String badge = WheelUi.upper(tr("adaptionwheel.gui.menu_coming_soon"));
        int badgeW = font.width(WheelUi.mono(WheelUi.MONO_8, badge)) + 14;
        int badgeX = x + w - 12 - badgeW;
        int titleMax = Math.max(36, badgeX - (x + 40) - 8);

        WheelUi.text(graphics, font, WheelUi.MONO_9,
                WheelUi.fit(font, WheelUi.MONO_9,
                        WheelUi.upper(tr("adaptionwheel.gui.menu_abilities")), titleMax),
                x + 40, y + 9, INK, false);
        WheelUi.text(graphics, font, WheelUi.MONO_8,
                WheelUi.fit(font, WheelUi.MONO_8, tr("adaptionwheel.gui.menu_abilities_hint"),
                        w - 54),
                x + 40, y + 25, MUTED, false);

        WheelUi.fillRounded(graphics, badgeX, y + 9, badgeW, 14, 7, ROSE_BG);
        WheelUi.centered(graphics, font, WheelUi.MONO_8, badge, badgeX + badgeW / 2,
                y + 12, ROSE, false);
    }

    private void drawAbilityCard(GuiGraphics graphics, ActiveAbility ability, int x, int y,
                                 int w, int h) {
        int accent = safeColor(ability.color());
        ActiveAbility choice = AbilityAvailability.selected();
        boolean selectedAbility = choice != null && choice.id().equals(ability.id());
        WheelUi.card(graphics, x, y, w, h, 8, CARD,
                selectedAbility ? WheelUi.withAlpha(accent, 0.55f) : WheelUi.BORDER);
        if (selectedAbility) {
            float pulse = 0.5f + 0.5f * Mth.sin((Util.getMillis() - openedAt) / 220f);
            WheelUi.outlineRounded(graphics, x, y, w, h, 8,
                    WheelUi.withAlpha(accent, 0.16f + pulse * 0.12f), CARD);
        }
        graphics.fill(x + 4, y + 9, x + 6, y + h - 9, accent);

        WheelUi.fillRounded(graphics, x + 10, y + 5, 26, 26, 6,
                WheelUi.tint(accent));
        WheelUi.itemIcon(graphics, ability.iconStack(), x + 13, y + 8, 20);

        String key = AbilityAvailability.keyLabel(ability);
        int nameMax = w - (key.isBlank() ? 58 : 92);
        WheelUi.text(graphics, font, WheelUi.MONO_11,
                WheelUi.fit(font, WheelUi.MONO_11, WheelUi.upper(ability.name().getString()), nameMax),
                x + 44, y + 6, INK, false);
        WheelUi.text(graphics, font, WheelUi.MONO_8,
                WheelUi.fit(font, WheelUi.MONO_8, ability.description().getString(), nameMax),
                x + 44, y + 20, MUTED, false);

        if (!key.isBlank()) {
            int keyW = Math.max(16, font.width(WheelUi.mono(WheelUi.MONO_8, key)) + 8);
            int keyX = x + w - 10 - keyW;
            WheelUi.fillRounded(graphics, keyX, y + 10, keyW, 16, 5,
                    WheelUi.withAlpha(accent, 0.13f));
            WheelUi.centered(graphics, font, WheelUi.MONO_8, key, keyX + keyW / 2,
                    y + 14, accent, false);
        }
    }

    private void drawGroupHeader(GuiGraphics graphics, AdaptationDomain domain, int x, int y, int w) {
        int accent = safeColor(domain.getColor());
        WheelUi.fillRounded(graphics, x + 1, y + 10, 6, 6, 2, accent);

        String label = WheelUi.upper(domain.translation().getString());
        boolean selectedInGroup = selected != null
                && AdaptationRegistry.domainOf(selected) == domain;
        WheelUi.trackedLeft(graphics, font, WheelUi.MONO_8, label, x + 13, y + 3,
                selectedInGroup ? INK_SOFT : MUTED, 1.5f);

        int count = 0;
        for (Row row : rows) {
            if (row.kind == Kind.CONCEPT && row.domain == domain) {
                count++;
            }
        }
        String countText = String.valueOf(count);
        int countWidth = font.width(WheelUi.mono(WheelUi.MONO_8, countText));
        int countX = x + w - countWidth;
        WheelUi.text(graphics, font, WheelUi.MONO_8, countText, countX, y + 3, FAINT, false);

        int labelWidth = WheelUi.trackedWidth(font, WheelUi.MONO_8, label, 1.5f);
        int lineStart = x + 13 + labelWidth + 9;
        int lineEnd = countX - 9;
        if (lineEnd > lineStart) {
            graphics.fill(lineStart, y + 7, lineEnd, y + 8, 0x1FFFFFFF);
        }
    }

    private void drawCard(GuiGraphics graphics, String concept, int x, int y, int w, int h) {
        boolean chosen = concept.equals(selected);
        boolean hoveredCard = concept.equals(hovered);
        boolean enabled = ClientAdaption.isEnabled(concept);
        int accent = safeColor(Concepts.color(concept));
        int effective = enabled ? accent : WheelUi.disabled(accent);

        if (chosen) {
            WheelUi.fillRounded(graphics, x - 3, y - 2, w + 6, h + 4, 11,
                    WheelUi.withAlpha(accent, 0.055f));
        }
        WheelUi.card(graphics, x, y, w, h, 9, CARD,
                chosen ? WheelUi.withAlpha(accent, 0.55f)
                        : hoveredCard ? 0x44FFFFFF : 0x30FFFFFF);

        if (chosen || hoveredCard) {
            float pulse = 0.5f + 0.5f * Mth.sin((Util.getMillis() - openedAt) / 260f);
            WheelUi.outlineRounded(graphics, x, y, w, h, 9,
                    WheelUi.withAlpha(accent, (chosen ? 0.20f : 0.10f) + pulse * 0.10f),
                    CARD);
        }

        graphics.fill(x + 4, y + 12, x + 6, y + h - 12, effective);

        if (chosen || hoveredCard) {
            for (int i = 0; i < 2; i++) {
                WheelUi.fillRounded(graphics, x + 12 - i * 3, y + 10 - i * 3,
                        38 + i * 6, 38 + i * 6, 11 - i * 2,
                        WheelUi.withAlpha(accent, 0.035f));
            }
        }
        WheelUi.fillRounded(graphics, x + 14, y + 12, 34, 34, 8,
                WheelUi.tint(effective));
        WheelUi.outlineRounded(graphics, x + 14, y + 12, 34, 34, 8,
                WheelUi.withAlpha(effective, 0.18f), WheelUi.tint(effective));
        ItemStack stack = icon(concept);
        if (stack.isEmpty()) {
            WheelUi.centered(graphics, font, WheelUi.MONO_11, "?",
                    x + 31, y + 25, effective, false);
        } else {
            WheelUi.itemIcon(graphics, stack, x + 19, y + 17, 24);
        }
        if (!enabled) {
            WheelUi.fillRounded(graphics, x + 14, y + 12, 34, 34, 8, 0x66000000);
        }

        int nameMax = Math.max(30, w - 136);
        WheelUi.text(graphics, font, WheelUi.MONO_11,
                WheelUi.fit(font, WheelUi.MONO_11, WheelUi.upper(menuName(concept).getString()), nameMax),
                x + 58, y + 11, enabled ? INK : FAINT, false);

        AdaptionTask task = taskFor(concept);
        String subtitle = WheelUi.upper(AdaptationRegistry.domainOf(concept).translation().getString())
                + "  \u00B7  " + stateText(concept, task);
        int subtitleColor = task != null ? WheelUi.AMBER : (enabled ? MUTED : FAINT);
        WheelUi.text(graphics, font, WheelUi.MONO_8,
                WheelUi.fit(font, WheelUi.MONO_8, subtitle, Math.max(40, w - 140)),
                x + 58, y + 27, subtitleColor, false);

        int progressX = x + 58;
        int progressW = Math.max(20, w - 74);
        int progressY = y + 44;
        graphics.fill(progressX, progressY, progressX + progressW, progressY + 1, 0x24FFFFFF);
        float progress = task != null
                ? Mth.clamp(ClientAdaption.taskProgress(task), 0f, 1f)
                : (ClientAdaption.isAdapted(concept)
                        ? 1f
                        : ClientAdaption.level(concept) / (float) MAX_LEVEL);
        int fill = Math.round(progressW * Mth.clamp(progress, 0f, 1f));
        if (fill > 0) {
            int barColor = task != null ? WheelUi.AMBER : (enabled ? effective : 0xFF5A5A62);
            graphics.fill(progressX, progressY, progressX + fill, progressY + 1, barColor);
        }
        if (Concepts.isLevelBased(concept)) {
            for (int i = 1; i < MAX_LEVEL; i++) {
                int tickX = progressX + Math.round(progressW * i / (float) MAX_LEVEL);
                graphics.fill(tickX, progressY - 1, tickX + 1, progressY + 2, 0x2EFFFFFF);
            }
        }

        boolean hasSwitch = canToggle(concept);
        Rect switchHit = Rect.EMPTY;
        if (hasSwitch) {
            int switchX = x + w - 46;
            int switchY = y + 11;
            boolean on = ClientAdaption.isEnabled(concept);
            String onOff = tr(on ? "adaptionwheel.gui.menu_on" : "adaptionwheel.gui.menu_off");
            int onOffW = font.width(WheelUi.mono(WheelUi.MONO_8, onOff));
            WheelUi.text(graphics, font, WheelUi.MONO_8, onOff, switchX - 8 - onOffW,
                    y + 15, on ? INK_SOFT : FAINT, false);
            boolean switchHovered = lastMouseX >= switchX - 2 && lastMouseX < switchX + 36
                    && lastMouseY >= switchY - 2 && lastMouseY < switchY + 20;
            WheelUi.toggle(graphics, switchX, switchY, on, switchHovered);
            switchHit = new Rect(switchX - 26, switchY - 4, 64, 24);
        }

        cards.add(new CardView(concept, new Rect(x, y, w, h), switchHit, hasSwitch));
    }

    private void drawFades(GuiGraphics graphics) {
        int max = maxScroll();
        if (max <= 0) {
            return;
        }
        // These bands used to be white, which read as a light source glowing out of the paper at
        // both ends of the list. They are meant to hide a card crossing the scroll viewport, not
        // brighten it, so they now fade into the same near-black the page is drawn on.
        if (scroll > 2) {
            for (int i = 0; i < 8; i++) {
                int alpha = (int) (255 * 0.10f * (8 - i));
                graphics.fill(listX, scrollViewportTop + i, listX + listW,
                        scrollViewportTop + i + 1, (alpha << 24) | 0x070708);
            }
        }
        if (scroll < max - 2) {
            for (int i = 0; i < 8; i++) {
                int alpha = (int) (255 * 0.10f * (i + 1));
                int y = scrollViewportBottom - 8 + i;
                graphics.fill(listX, y, listX + listW, y + 1, (alpha << 24) | 0x111114);
            }
        }
    }

    private void drawScrollbar(GuiGraphics graphics) {
        if (maxScroll() <= 0) {
            return;
        }
        int trackX = listX + listW + 8;
        int top = scrollViewportTop;
        int heightPx = scrollViewportBottom - scrollViewportTop;
        if (heightPx <= 0) {
            return;
        }
        trackHit = new Rect(trackX - 5, top, 12, heightPx);

        int thumbHeight = Math.max(24, (int) (heightPx * (heightPx / (double) contentHeight())));
        thumbHeight = Math.min(heightPx, thumbHeight);
        int max = maxScroll();
        int thumbY = top + (int) Math.round((scroll / (double) max) * (heightPx - thumbHeight));

        graphics.fill(trackX, top, trackX + 2, top + heightPx, 0x1AFFFFFF);
        boolean hot = draggingScrollbar || trackHit.contains(lastMouseX, lastMouseY);
        WheelUi.fillRounded(graphics, trackX, thumbY, 2, thumbHeight, 1,
                hot ? 0x66FFFFFF : 0x44FFFFFF);
    }

    private void drawEmpty(GuiGraphics graphics) {
        int centerX = listX + listW / 2;
        int top = scrollViewportTop + 28;
        boolean searching = !search.isBlank();
        if (searching) {
            WheelUi.ringCircle(graphics, centerX, top + 12, 10, FAINT);
            graphics.fill(centerX + 8, top + 20, centerX + 16, top + 21, FAINT);
        } else {
            WheelUi.itemIcon(graphics, wheelIcon(), centerX - 14, top - 2, 28);
        }
        String title = searching
                ? tr("adaptionwheel.gui.menu_no_matches")
                : tr("adaptionwheel.gui.menu_empty_title");
        String desc = searching
                ? tr("adaptionwheel.gui.menu_no_matches_desc")
                : tr("adaptionwheel.gui.menu_empty_desc");
        WheelUi.trackedCentered(graphics, font, WheelUi.MONO_11, WheelUi.upper(title),
                centerX, top + 38, INK_SOFT, 1.2f);
        centeredWrapped(graphics, Component.literal(desc), centerX, top + 56,
                Math.max(120, listW - 60), 10, 3, MUTED);
    }

    private void drawFooter(GuiGraphics graphics) {
        graphics.fill(margin, footerY - 9, width - margin, footerY - 8, 0x1FFFFFFF);
        // The old author credit used to live here. It said nothing the player could act on,
        // so the only permanent footer status is the one that answers a question the wheel
        // cannot otherwise answer: is Instabreak currently on? It is drawn only once the
        // netherite fist has unlocked it, so an unavailable ability does not advertise itself.
        if (ClientAdaption.wearingWheel && ClientAdaption.instabreakUnlocked && width >= 420) {
            boolean active = ClientAdaption.instabreakActive;
            int color = active ? GREEN : MUTED;
            WheelUi.fillRounded(graphics, margin, footerY + 3, 3, 3, 1,
                    WheelUi.withAlpha(color, 0.65f));
            WheelUi.trackedLeft(graphics, font, WheelUi.MONO_8,
                    WheelUi.upper(tr(active
                            ? "adaptionwheel.instabreak.on"
                            : "adaptionwheel.instabreak.off")),
                    margin + 8, footerY, color, 1.0f);
        }

        String hint = width >= 900
                ? tr("adaptionwheel.gui.menu_footer_hint")
                : tr("adaptionwheel.gui.menu_footer_short");
        WheelUi.centered(graphics, font, WheelUi.MONO_8, hint, width / 2, footerY, MUTED, false);

        String stats = search.isBlank()
                ? tr("adaptionwheel.gui.menu_footer_stats", acquiredCount(), activeCount())
                : tr("adaptionwheel.gui.menu_footer_matches", visibleConceptCount());
        int statsWidth = font.width(WheelUi.mono(WheelUi.MONO_8, stats));
        WheelUi.text(graphics, font, WheelUi.MONO_8, stats,
                width - margin - statsWidth, footerY, FAINT, false);
    }

    private int centeredWrapped(GuiGraphics graphics, Component text, int centerX, int y,
                                int maxWidth, int lineGap, int maxLines, int color) {
        List<FormattedCharSequence> lines = font.split(
                WheelUi.mono(WheelUi.MONO_8, text), Math.max(1, maxWidth));
        int count = Math.min(maxLines, lines.size());
        for (int i = 0; i < count; i++) {
            FormattedCharSequence line = lines.get(i);
            graphics.drawString(font, line, centerX - font.width(line) / 2, y + i * lineGap,
                    color, false);
        }
        return count;
    }

    private String stateText(String concept, AdaptionTask task) {
        if (task != null) {
            int percent = Math.round(Mth.clamp(ClientAdaption.taskProgress(task), 0f, 1f) * 100f);
            return tr("adaptionwheel.gui.menu_analyzing") + " " + percent + "%";
        }
        if (!ClientAdaption.isEnabled(concept)) {
            return tr("adaptionwheel.gui.menu_off");
        }
        if (ClientAdaption.isAdapted(concept)) {
            return tr("adaptionwheel.gui.menu_adapted");
        }
        int level = ClientAdaption.level(concept);
        if (level > 0) {
            if (level >= MAX_LEVEL) {
                return tr("adaptionwheel.gui.menu_max");
            }
            return tr("adaptionwheel.gui.menu_level", level, MAX_LEVEL);
        }
        return tr("adaptionwheel.gui.menu_locked");
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (clearRect.contains(mouseX, mouseY)) {
            search = "";
            searchCaret = 0;
            searchStart = 0;
            searchFocused = true;
            refreshRows(true);
            return true;
        }
        if (searchRect.contains(mouseX, mouseY)) {
            searchFocused = true;
            searchCaret = caretAt(mouseX);
            return true;
        }
        if (searchFocused) {
            searchFocused = false;
        }

        if (mouseX >= tabX && mouseX < tabX + tabW
                && mouseY >= tabY && mouseY < tabY + tabViewportH) {
            for (TabView tab : tabHits) {
                if (tab.rect.contains(mouseX, mouseY)) {
                    selectedTab = tab.domain;
                    refreshRows(true);
                    return true;
                }
            }
        }

        if (leftToggle.contains(mouseX, mouseY)) {
            toggle(focusConcept());
            return true;
        }

        for (CardView card : cards) {
            if (mouseY < scrollViewportTop || mouseY >= scrollViewportBottom) {
                continue;
            }
            if (card.hasSwitch && card.toggle.contains(mouseX, mouseY)) {
                selected = card.concept;
                toggle(card.concept);
                return true;
            }
            if (card.card.contains(mouseX, mouseY)) {
                selected = card.concept;
                return true;
            }
        }

        for (AbilityHit hit : abilityHits) {
            if (hit.rect.contains(mouseX, mouseY)) {
                ActiveAbilities.select(hit.id);
                return true;
            }
        }

        if (trackHit.contains(mouseX, mouseY)) {
            int top = scrollViewportTop;
            int heightPx = scrollViewportBottom - scrollViewportTop;
            int thumbHeight = Math.max(24, (int) (heightPx * (heightPx / (double) contentHeight())));
            thumbHeight = Math.min(heightPx, thumbHeight);
            int thumbY = top + (int) Math.round((scroll / (double) Math.max(1, maxScroll()))
                    * (heightPx - thumbHeight));
            scrollGrab = mouseY >= thumbY && mouseY < thumbY + thumbHeight
                    ? mouseY - thumbY
                    : thumbHeight / 2.0;
            draggingScrollbar = true;
            dragScrollTo(mouseY);
            return true;
        }

        if (placeholderRect.contains(mouseX, mouseY)) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= tabX - 3 && mouseX < tabX + tabW + 4
                && mouseY >= tabY && mouseY < tabY + tabViewportH) {
            tabScroll = Mth.clamp(tabScroll - scrollY * 24.0, 0, maxTabScroll());
            return true;
        }
        if (mouseX >= listX - 8 && mouseX < listX + listW + 18
                && mouseY >= listTop && mouseY < listBottom) {
            scrollTarget = Mth.clamp(scrollTarget - scrollY * 36.0, 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            dragScrollTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void dragScrollTo(double mouseY) {
        int top = scrollViewportTop;
        int heightPx = scrollViewportBottom - scrollViewportTop;
        int thumbHeight = Math.max(24, (int) (heightPx * (heightPx / (double) contentHeight())));
        thumbHeight = Math.min(heightPx, thumbHeight);
        double travel = Math.max(1, heightPx - thumbHeight);
        double t = Mth.clamp((mouseY - top - scrollGrab) / travel, 0.0, 1.0);
        scrollTarget = t * maxScroll();
        scroll = scrollTarget;
    }

    private void dragScrollBy(double amount) {
        scrollTarget = Mth.clamp(scrollTarget + amount, 0, maxScroll());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (!search.isEmpty()) {
                    search = "";
                    searchCaret = 0;
                    refreshRows(true);
                } else {
                    searchFocused = false;
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                searchFocused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (searchCaret > 0) {
                    search = search.substring(0, searchCaret - 1) + search.substring(searchCaret);
                    searchCaret--;
                    refreshRows(true);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                if (searchCaret < search.length()) {
                    search = search.substring(0, searchCaret) + search.substring(searchCaret + 1);
                    refreshRows(true);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                searchCaret = Math.max(0, searchCaret - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                searchCaret = Math.min(search.length(), searchCaret + 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_HOME) {
                searchCaret = 0;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_END) {
                searchCaret = search.length();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_V && hasControlDown()) {
                insertText(minecraft != null && minecraft.keyboardHandler != null
                        ? minecraft.keyboardHandler.getClipboard() : "");
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_A && hasControlDown()) {
                search = "";
                searchCaret = 0;
                refreshRows(true);
                return true;
            }
            // Every other key is consumed while the field owns focus. Otherwise Space would reach
            // the screen's own shortcut and toggle an adaptation while the player is typing one.
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_SLASH
                || (keyCode == GLFW.GLFW_KEY_F && hasControlDown())) {
            searchFocused = true;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            moveSelection(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            moveSelection(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            dragScrollBy(scrollViewportBottom - scrollViewportTop - 40);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            dragScrollBy(-(scrollViewportBottom - scrollViewportTop - 40));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                || keyCode == GLFW.GLFW_KEY_SPACE) {
            toggle(selected);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!searchFocused) {
            return false;
        }
        if (search.length() >= 48 || Character.isISOControl(codePoint)) {
            return true;
        }
        insertText(String.valueOf(codePoint));
        return true;
    }

    private void insertText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String clipped = text.length() > 48 - search.length()
                ? text.substring(0, Math.max(0, 48 - search.length()))
                : text;
        if (clipped.isEmpty()) {
            return;
        }
        search = search.substring(0, searchCaret) + clipped + search.substring(searchCaret);
        searchCaret += clipped.length();
        refreshRows(true);
    }

    private int caretAt(double mouseX) {
        int maxWidth = Math.max(1, searchRect.w - 38);
        int start = Mth.clamp(searchStart, 0, search.length());
        int rel = (int) Math.max(0, mouseX - (searchRect.x + 19));
        int best = start;
        for (int i = start + 1; i <= search.length(); i++) {
            int width = font.width(WheelUi.mono(WheelUi.MONO_8, search.substring(start, i)));
            if (width > rel) {
                break;
            }
            best = i;
        }
        if (rel > maxWidth) {
            best = search.length();
        }
        return best;
    }

    private void moveSelection(int direction) {
        if (visible.isEmpty()) {
            return;
        }
        int index = selected == null ? -1 : visible.indexOf(selected);
        int next = index < 0 ? 0 : Mth.clamp(index + direction, 0, visible.size() - 1);
        selected = visible.get(next);
        ensureVisible(selected);
    }

    private void ensureVisible(String concept) {
        int y = 0;
        for (Row row : rows) {
            if (row.kind == Kind.CONCEPT && concept.equals(row.concept)) {
                break;
            }
            y += row.kind == Kind.GROUP ? GROUP_HEIGHT : CARD_STRIDE;
        }
        int viewport = scrollViewportBottom - scrollViewportTop;
        if (y < scrollTarget) {
            scrollTarget = y;
        } else if (y + CARD_HEIGHT > scrollTarget + viewport) {
            scrollTarget = y + CARD_HEIGHT - viewport;
        }
        scrollTarget = Mth.clamp(scrollTarget, 0, maxScroll());
    }

    private void toggle(String concept) {
        if (!canToggle(concept)) {
            return;
        }
        // Server-authoritative on purpose. An optimistic local flip here would make the switch the
        // only thing that knows the new state, and a rejected payload would leave it lying until
        // the next sync. The toggle payload replies with a full sync immediately.
        ToggleAdaptationPayload.send(concept);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public boolean isSearchFocused() {
        return searchFocused;
    }

    private static int safeColor(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int luminance = (r * 299 + g * 587 + b * 114) / 1000;
        if (luminance >= 240) {
            // Existence is pure white in the registry. A warm white keeps it distinct from the
            // paper-coloured ink without turning the card into a light-theme island.
            return 0xFFE8E2D6;
        }
        if (luminance <= 40) {
            // A dark domain colour would disappear on a dark card. Lift it towards the warm
            // neutral the rest of the palette uses instead of dropping it to grey.
            return WheelUi.mix(color, 0xFFB8B4AA, 0.55f);
        }
        return color;
    }

    private enum Kind {
        GROUP, CONCEPT
    }

    private record Row(Kind kind, AdaptationDomain domain, String concept) {
    }

    private record CardView(String concept, Rect card, Rect toggle, boolean hasSwitch) {
    }

    private record TabView(AdaptationDomain domain, Rect rect) {
    }

    private record AbilityHit(String id, Rect rect) {
    }

    private record Rect(int x, int y, int w, int h) {
        static final Rect EMPTY = new Rect(0, 0, 0, 0);

        boolean contains(double mouseX, double mouseY) {
            return w > 0 && h > 0 && mouseX >= x && mouseX < x + w
                    && mouseY >= y && mouseY < y + h;
        }
    }
}
