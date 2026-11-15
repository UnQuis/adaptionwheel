package ru.adaptionwheel.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read and move a container screen's panel.
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccess {

    @Accessor("leftPos")
    int adaptionwheel$getLeftPos();

    @Accessor("leftPos")
    void adaptionwheel$setLeftPos(int value);

    @Accessor("topPos")
    int adaptionwheel$getTopPos();

    @Accessor("topPos")
    void adaptionwheel$setTopPos(int value);

    @Accessor("imageWidth")
    int adaptionwheel$getImageWidth();

    @Accessor("imageHeight")
    int adaptionwheel$getImageHeight();
}
