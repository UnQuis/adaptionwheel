package ru.adaptionwheel.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read and move a container screen's panel.
 *
 * <p>The cache is drawn under whatever container is open, which only works if the container is
 * moved up to make room. {@code leftPos}/{@code topPos} are {@code protected}, and the whole
 * screen — background, slots, labels — is positioned from them, so moving them is the one change
 * that keeps everything consistent. Wrapping the screen instead would mean re-implementing the
 * event chain of every container in the game.
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
