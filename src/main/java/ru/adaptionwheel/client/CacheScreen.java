package ru.adaptionwheel.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.CraftingRecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import org.jetbrains.annotations.Nullable;
import ru.adaptionwheel.menu.CacheMenu;
import ru.adaptionwheel.server.CacheService;

/**
 * The adapted inventory screen: the vanilla inventory, unchanged and in its usual place, with the
 * cache extension below it.
 *
 * <p>Opening the inventory with {@code E} is intercepted client-side and routed to the server menu,
 * so this is not a second, parallel inventory — it is the inventory plus extra cache slots. The
 * vanilla texture, player preview, armour/offhand slots, crafting grid and recipe book follow the
 * 26.3 inventory/recipe-book screen pipeline; the cache extension is drawn here.
 */
public class CacheScreen extends AbstractContainerScreen<CacheMenu> implements RecipeUpdateListener {

    private static final Identifier INVENTORY_LOCATION =
            Identifier.withDefaultNamespace("textures/gui/container/inventory.png");

    private final CraftingRecipeBookComponent recipeBookComponent;
    private final EffectsInInventory effects;
    private final boolean sideLayout;
    private boolean widthTooNarrow;
    private float xMouse;
    private float yMouse;

    public CacheScreen(CacheMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title,
                chooseSideLayout() ? CacheService.SIDE_WIDTH : CacheService.PANEL_WIDTH,
                chooseSideLayout() ? CacheService.SIDE_HEIGHT : CacheService.PANEL_HEIGHT);
        this.sideLayout = this.imageWidth == CacheService.SIDE_WIDTH;
        this.titleLabelX = 97;
        this.inventoryLabelY = -100;
        this.recipeBookComponent = new CraftingRecipeBookComponent(menu);
        this.effects = new EffectsInInventory(this);
        menu.relayoutCache(this.sideLayout);
    }

    private static boolean chooseSideLayout() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        int h = mc.getWindow() == null ? 0 : mc.getWindow().getGuiScaledHeight();
        return h > 0 && h < CacheService.PANEL_HEIGHT + 16;
    }

    @Override
    protected void init() {
        super.init();
        this.widthTooNarrow = this.width < 379;
        // Ensure slot layout matches the chosen panel shape even if init is called multiple times.
        this.menu.relayoutCache(this.sideLayout);
        this.recipeBookComponent.init(this.width, this.height, this.minecraft, this.widthTooNarrow);
        this.leftPos = this.recipeBookComponent.updateScreenPosition(this.width, this.imageWidth);
        initRecipeBookButton();
        this.addWidget(this.recipeBookComponent);
    }

    private void initRecipeBookButton() {
        ScreenPosition buttonPos = getRecipeBookButtonPosition();
        this.addRenderableWidget(new ImageButton(buttonPos.x(), buttonPos.y(), 20, 18,
                RecipeBookComponent.RECIPE_BUTTON_SPRITES, button -> {
            this.recipeBookComponent.toggleVisibility();
            this.leftPos = this.recipeBookComponent.updateScreenPosition(this.width, this.imageWidth);
            ScreenPosition updatedButtonPos = getRecipeBookButtonPosition();
            button.setPosition(updatedButtonPos.x(), updatedButtonPos.y());
        }));
    }

    protected ScreenPosition getRecipeBookButtonPosition() {
        // Keep vanilla's offset from the inventory's vertical centre, not the screen's: this panel
        // is taller than the vanilla texture in vertical layout.
        return new ScreenPosition(this.leftPos + 104, this.topPos + 61);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.effects.extractRenderState(graphics, mouseX, mouseY);
        if (this.recipeBookComponent.isVisible() && this.widthTooNarrow) {
            this.extractBackground(graphics, mouseX, mouseY, a);
        } else {
            super.extractContents(graphics, mouseX, mouseY, a);
        }

        graphics.nextStratum();
        this.recipeBookComponent.extractRenderState(graphics, mouseX, mouseY, a);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new net.neoforged.neoforge.client.event.ScreenEvent.Render.Foreground(this, graphics, mouseX, mouseY, a));
        graphics.nextStratum();
        this.extractCarriedItem(graphics, mouseX, mouseY);
        this.extractTooltip(graphics, mouseX, mouseY);
        this.recipeBookComponent.extractTooltip(graphics, mouseX, mouseY, this.hoveredSlot);

        this.xMouse = mouseX;
        this.yMouse = mouseY;
    }

    @Override
    protected void extractSlots(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractSlots(graphics, mouseX, mouseY);
        this.recipeBookComponent.extractGhostRecipe(graphics, false);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int xo = this.leftPos;
        int yo = this.topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_LOCATION, xo, yo, 0.0F, 0.0F,
                CacheService.PANEL_WIDTH, CacheService.VANILLA_HEIGHT, 256, 256);
        if (this.minecraft != null && this.minecraft.player != null) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(graphics,
                    xo + 26, yo + 8, xo + 75, yo + 78, 30, 0.0625F,
                    this.xMouse, this.yMouse, this.minecraft.player);
        }

        // The cache extension is the same art as the inventory it hangs off.
        if (sideLayout) {
            CachePanel.draw(graphics, xo + CacheService.SIDE_CACHE_X, yo + CacheService.SIDE_CACHE_Y);
        } else {
            CachePanel.drawJoined(graphics, xo, yo + CacheService.VANILLA_HEIGHT);
        }
    }

    /**
     * Labels are drawn inside the panel translation in 26.3 container screens.
     */
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFF404040, false);
        int labelX = sideLayout ? CacheService.SIDE_CACHE_X + 8 : 8;
        int labelY = sideLayout ? CacheService.SIDE_CACHE_Y + 4 : CacheService.CACHE_LABEL_Y;
        graphics.text(this.font, Component.translatable("adaptionwheel.gui.cache"),
                labelX, labelY, 0xFF404040, false);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return this.recipeBookComponent.charTyped(event) || super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return this.recipeBookComponent.keyPressed(event) || super.keyPressed(event);
    }

    @Override
    public boolean isInputCaptured() {
        return super.isInputCaptured() || this.recipeBookComponent.capturesInput();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.recipeBookComponent.mouseClicked(event, doubleClick)) {
            this.setFocused(this.recipeBookComponent);
            return true;
        }
        return this.widthTooNarrow && this.recipeBookComponent.isVisible()
                ? true
                : super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return this.recipeBookComponent.mouseDragged(event, dx, dy) || super.mouseDragged(event, dx, dy);
    }

    @Override
    protected boolean isHovering(int left, int top, int w, int h, double xm, double ym) {
        return (!this.widthTooNarrow || !this.recipeBookComponent.isVisible())
                && super.isHovering(left, top, w, h, xm, ym);
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int xo, int yo) {
        boolean clickedOutside = mx < xo || my < yo || mx >= xo + this.imageWidth || my >= yo + this.imageHeight;
        return this.recipeBookComponent.hasClickedOutside(mx, my, this.leftPos, this.topPos,
                this.imageWidth, this.imageHeight) && clickedOutside;
    }

    @Override
    protected void slotClicked(@Nullable Slot slot, int slotId, int buttonNum, ContainerInput containerInput) {
        super.slotClicked(slot, slotId, buttonNum, containerInput);
        this.recipeBookComponent.slotClicked(slot);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        this.recipeBookComponent.tick();
    }

    @Override
    public void recipesUpdated() {
        this.recipeBookComponent.recipesUpdated();
    }

    @Override
    public void fillGhostRecipe(RecipeDisplay display) {
        this.recipeBookComponent.fillGhostRecipe(display);
    }

    public boolean showsActiveEffects() {
        return this.effects.canSeeEffects();
    }
}
