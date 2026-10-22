package ru.adaptionwheel.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.menu.CacheMenu;
import ru.adaptionwheel.server.CacheService;

/**
 * The adapted inventory screen: the vanilla inventory, unchanged and in its usual place, with the
 * cache extension below it (or beside it when the window is too short).
 *
 * <p>Opening the inventory with {@code E} is intercepted client-side and routed to the server menu,
 * so this is not a second, parallel inventory — it is the inventory plus fifty more slots. The
 * vanilla texture, player preview, armour/offhand slots, crafting grid and recipe book are all the
 * real thing; only the cache extension is drawn here.
 */
public class CacheScreen extends AbstractContainerScreen<CacheMenu> implements RecipeUpdateListener {

    private static final ResourceLocation CACHE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID,
                    "textures/gui/container/cache.png");

    private final RecipeBookComponent recipeBookComponent = new RecipeBookComponent();

    private boolean sideLayout;
    private boolean widthTooNarrow;
    private boolean buttonClicked;
    private float xMouse;
    private float yMouse;

    public CacheScreen(CacheMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.titleLabelX = 97;
        this.inventoryLabelY = -100;
    }

    @Override
    protected void init() {
        // Pick the layout before super.init(), because AbstractContainerScreen centres the panel
        // using imageWidth/imageHeight.
        sideLayout = height < CacheService.PANEL_HEIGHT + 16;
        imageWidth = sideLayout ? CacheService.SIDE_WIDTH : CacheService.PANEL_WIDTH;
        imageHeight = sideLayout ? CacheService.SIDE_HEIGHT : CacheService.PANEL_HEIGHT;
        menu.relayoutCache(sideLayout);
        super.init();

        widthTooNarrow = width < 379;
        recipeBookComponent.init(width, height, minecraft, widthTooNarrow, menu);
        leftPos = recipeBookComponent.updateScreenPosition(width, imageWidth);
        // The button keeps vanilla's offset from the *inventory's* vertical centre, not the
        // screen's: this panel is taller than the vanilla texture, so height/2 would float it
        // below the slots it belongs to.
        addRenderableWidget(new ImageButton(leftPos + 104, topPos + 61, 20, 18,
                RecipeBookComponent.RECIPE_BUTTON_SPRITES, button -> {
                    recipeBookComponent.toggleVisibility();
                    leftPos = recipeBookComponent.updateScreenPosition(width, imageWidth);
                    button.setPosition(leftPos + 104, topPos + 61);
                    buttonClicked = true;
                }));
        addWidget(recipeBookComponent);
    }

    @Override
    public void containerTick() {
        recipeBookComponent.tick();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;

        // The exact vanilla inventory texture and player preview.
        graphics.blit(INVENTORY_LOCATION, left, top, 0, 0, 176, 166);
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
                left + 26, top + 8, left + 75, top + 78, 30, 0.0625F,
                xMouse, yMouse, minecraft.player);

        // The cache extension is the same art as the inventory it hangs off: its texture is built
        // from inventory.png (frame, bevel and slot sprite), so there is no visible seam in style,
        // only the divider the inventory's own bottom edge already draws.
        if (sideLayout) {
            graphics.blit(CACHE_TEXTURE, left + CacheService.SIDE_CACHE_X, top, 0, 0,
                    CacheService.PANEL_WIDTH, CacheService.CACHE_TEXTURE_HEIGHT);
        } else {
            graphics.blit(CACHE_TEXTURE, left, top + CacheService.VANILLA_HEIGHT, 0, 0,
                    CacheService.PANEL_WIDTH, CacheService.CACHE_TEXTURE_HEIGHT);
        }
    }

    /**
     * Labels are drawn inside the panel translation, so these coordinates are relative to
     * {@code leftPos/topPos}. Adding them again — which is correct in {@link #renderBg} — puts the
     * text a full panel away from the panel it belongs to.
     */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 4210752, false);
        int labelX = sideLayout ? CacheService.SIDE_CACHE_X + 8 : 8;
        int labelY = sideLayout ? 2 : CacheService.CACHE_LABEL_Y;
        graphics.drawString(font, Component.translatable("adaptionwheel.gui.cache"),
                labelX, labelY, 4210752, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Vanilla's narrow-window behaviour: an open recipe book covers the inventory, so drawing
        // both would show two overlapping panels.
        if (recipeBookComponent.isVisible() && widthTooNarrow) {
            renderBackground(graphics, mouseX, mouseY, partialTick);
            recipeBookComponent.render(graphics, mouseX, mouseY, partialTick);
        } else {
            super.render(graphics, mouseX, mouseY, partialTick);
            recipeBookComponent.render(graphics, mouseX, mouseY, partialTick);
            recipeBookComponent.renderGhostRecipe(graphics, leftPos, topPos, false, partialTick);
        }

        renderTooltip(graphics, mouseX, mouseY);
        recipeBookComponent.renderTooltip(graphics, leftPos, topPos, mouseX, mouseY);
        xMouse = (float) mouseX;
        yMouse = (float) mouseY;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return recipeBookComponent.keyPressed(keyCode, scanCode, modifiers)
                || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return recipeBookComponent.charTyped(codePoint, modifiers)
                || super.charTyped(codePoint, modifiers);
    }

    @Override
    protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY) {
        return (!widthTooNarrow || !recipeBookComponent.isVisible())
                && super.isHovering(x, y, width, height, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (recipeBookComponent.mouseClicked(mouseX, mouseY, button)) {
            setFocused(recipeBookComponent);
            return true;
        }
        return widthTooNarrow && recipeBookComponent.isVisible()
                ? false
                : super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (buttonClicked) {
            buttonClicked = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int button) {
        boolean outside = mouseX < guiLeft || mouseY < guiTop
                || mouseX >= guiLeft + imageWidth || mouseY >= guiTop + imageHeight;
        return recipeBookComponent.hasClickedOutside(mouseX, mouseY, leftPos, topPos,
                imageWidth, imageHeight, button) && outside;
    }

    @Override
    protected void slotClicked(@Nullable Slot slot, int slotId, int mouseButton, ClickType type) {
        super.slotClicked(slot, slotId, mouseButton, type);
        recipeBookComponent.slotClicked(slot);
    }

    @Override
    public void recipesUpdated() {
        recipeBookComponent.recipesUpdated();
    }

    @Override
    public RecipeBookComponent getRecipeBookComponent() {
        return recipeBookComponent;
    }
}
