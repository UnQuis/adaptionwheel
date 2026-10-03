package ru.adaptionwheel.menu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.adaptionwheel.AdaptionWheel;

public final class ModMenus {

    private ModMenus() {
    }

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, AdaptionWheel.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<ResonanceAltarMenu>> RESONANCE_ALTAR_TRADE =
            MENUS.register("resonance_altar_trade", () -> IMenuTypeExtension.create(
                    (windowId, inventory, extra) -> new ResonanceAltarMenu(windowId, inventory, extra)));

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
