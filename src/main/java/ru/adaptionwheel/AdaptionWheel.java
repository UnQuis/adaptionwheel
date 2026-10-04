package ru.adaptionwheel;

import java.util.Arrays;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import ru.adaptionwheel.compat.CuriosCompat;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.data.AttachmentTypes;
import ru.adaptionwheel.data.ModDataComponents;
import ru.adaptionwheel.item.ModItems;
import ru.adaptionwheel.sound.ModSounds;

@Mod(AdaptionWheel.MODID)
public class AdaptionWheel {

    public static final String MODID = "adaptionwheel";

    private static boolean isTab(ResourceKey<CreativeModeTab> key, String id) {
        return key != null
                && key.identifier().equals(Identifier.withDefaultNamespace(id));
    }

    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    private static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = CREATIVE_TABS.register("adaptionwheel",
            () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup.adaptionwheel"))
                    .icon(() -> ModItems.MAHORAGA_WHEEL.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.MAHORAGA_WHEEL_WOOD.get());
                        output.accept(ModItems.MAHORAGA_WHEEL.get());
                        output.accept(ModItems.ALL_ADAPTION.get());
                        output.accept(ModItems.SWORD_OF_EXTERMINATION.get());

                        output.accept(ru.adaptionwheel.block.ModBlocks.ADAPTATION_BRAZIER_ITEM.get());
                        output.accept(ru.adaptionwheel.block.ModBlocks.WHEEL_TOTEM_ITEM.get());
                        output.accept(ru.adaptionwheel.block.ModBlocks.RESONANCE_ALTAR_ITEM.get());
                        output.accept(ru.adaptionwheel.entity.ModSpawnEggs.DISCIPLE_EGG.get());
                    })
                    .build());

    private static ModConfig.Type configType(String... preferredNames) {
        for (String name : preferredNames) {
            for (ModConfig.Type type : ModConfig.Type.values()) {
                if (type.name().equals(name)) {
                    return type;
                }
            }
        }
        throw new IllegalStateException("ModConfig.Type has none of " + Arrays.toString(preferredNames)
                + "; found " + Arrays.toString(ModConfig.Type.values()));
    }

    public AdaptionWheel(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(configType("SYNCED", "SERVER"), AdaptionConfig.SERVER_SPEC);
        modContainer.registerConfig(configType("CLIENT"), AdaptionConfig.CLIENT_SPEC);
        AttachmentTypes.ATTACHMENTS.register(modEventBus);
        ModDataComponents.COMPONENTS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ru.adaptionwheel.menu.ModMenus.register(modEventBus);
        ru.adaptionwheel.effect.ModEffects.EFFECTS.register(modEventBus);
        ru.adaptionwheel.block.ModBlocks.BLOCKS.register(modEventBus);
        ru.adaptionwheel.block.ModBlocks.BLOCK_ITEMS.register(modEventBus);

        ru.adaptionwheel.entity.ModEntities.ENTITIES.register(modEventBus);
        ru.adaptionwheel.entity.ModSpawnEggs.EGGS.register(modEventBus);
        ru.adaptionwheel.advancement.AdaptationTrigger.TRIGGERS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            Item item = ModItems.MAHORAGA_WHEEL.get();
            CuriosCompat.registerWheel(item);
        });
    }

    @EventBusSubscriber(modid = MODID)
    public static class ModBusEvents {

        @SubscribeEvent
        public static void onRegisterScreens(
                net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {

            event.register(ru.adaptionwheel.menu.ModMenus.RESONANCE_ALTAR_TRADE.get(),
                    ru.adaptionwheel.TradeScreen::new);
            event.register(ru.adaptionwheel.menu.ModMenus.CACHE.get(),
                    ru.adaptionwheel.client.CacheScreen::new);
        }

        @SubscribeEvent
        public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {

            if (isTab(event.getTabKey(), "functional_blocks")) {
                event.accept(ru.adaptionwheel.block.ModBlocks.ADAPTATION_BRAZIER_ITEM.get());
                event.accept(ru.adaptionwheel.block.ModBlocks.WHEEL_TOTEM_ITEM.get());
                event.accept(ru.adaptionwheel.block.ModBlocks.RESONANCE_ALTAR_ITEM.get());
            }
            if (isTab(event.getTabKey(), "spawn_eggs")) {
                event.accept(ru.adaptionwheel.entity.ModSpawnEggs.DISCIPLE_EGG.get());
            }
            if (isTab(event.getTabKey(), "combat")) {
                event.accept(ModItems.MAHORAGA_WHEEL_WOOD.get());
                event.accept(ModItems.MAHORAGA_WHEEL.get());
                event.accept(ModItems.ALL_ADAPTION.get());
                event.accept(ModItems.SWORD_OF_EXTERMINATION.get());
            }
        }
    }
}
