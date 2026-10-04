package ru.adaptionwheel.menu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

public class CacheMenuProvider implements MenuProvider {

    @Override
    public Component getDisplayName() {
        return Component.translatable("adaptionwheel.gui.cache");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int windowId, Inventory inventory, Player player) {
        return new CacheMenu(windowId, player);
    }
}