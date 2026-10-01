package ru.adaptionwheel.menu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.adaptionwheel.AdaptionWheel;

/**
 * The mod's {@link MenuType}s — its only container screen.
 *
 * <p>Registered through {@link IMenuTypeExtension#create} rather than the vanilla constructor, and
 * that is what lets the menu carry a {@link net.minecraft.core.BlockPos} without the stone having a
 * block entity: the position travels in the extra data the server sends when it opens the screen,
 * and the client factory reads it back out.</p>
 *
 * <p>The extra data is not optional, and the failure is quiet. If it is empty, the server sends a
 * plain open-screen packet and the client factory is handed an <b>empty buffer</b>, so reading a
 * position off it throws rather than returning something wrong. Hence
 * {@code DomainStoneBlock} always writes one.</p>
 */
public final class ModMenus {

    private ModMenus() {
    }

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, AdaptionWheel.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<DomainStoneMenu>> DOMAIN_STONE =
            MENUS.register("domain_stone", () -> IMenuTypeExtension.create(
                    (windowId, inventory, extra) -> new DomainStoneMenu(windowId, inventory, extra)));

    public static final DeferredHolder<MenuType<?>, MenuType<ResonanceAltarMenu>> RESONANCE_ALTAR_TRADE =
            MENUS.register("resonance_altar_trade", () -> IMenuTypeExtension.create(
                    (windowId, inventory, extra) -> new ResonanceAltarMenu(windowId, inventory, extra)));

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}