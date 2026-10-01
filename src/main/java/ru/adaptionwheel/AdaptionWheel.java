package ru.adaptionwheel;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.data.AttachmentTypes;
import ru.adaptionwheel.data.ModDataComponents;
import ru.adaptionwheel.item.ModItems;
import ru.adaptionwheel.sound.ModSounds;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

@Mod(AdaptionWheel.MODID)
public class AdaptionWheel {

    public static final String MODID = "adaptionwheel";

    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    private static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = CREATIVE_TABS.register("adaptionwheel",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.adaptionwheel"))
                    .icon(() -> ModItems.MAHORAGA_WHEEL.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.MAHORAGA_WHEEL_WOOD.get());
                        output.accept(ModItems.MAHORAGA_WHEEL.get());
                        output.accept(ModItems.ALL_ADAPTION.get());
                        output.accept(ModItems.SWORD_OF_EXTERMINATION.get());
                        // The ritual set and the mob, in the mod's own tab as well as the vanilla
                        // ones. This lambda is the one place that is guaranteed to run, so it is
                        // where content has to be listed for it to be findable at all.
                        output.accept(ru.adaptionwheel.block.ModBlocks.ADAPTATION_BRAZIER_ITEM.get());
                        output.accept(ru.adaptionwheel.block.ModBlocks.WHEEL_TOTEM_ITEM.get());
                        output.accept(ru.adaptionwheel.block.ModBlocks.RESONANCE_ALTAR_ITEM.get());
                        output.accept(ru.adaptionwheel.block.ModBlocks.DOMAIN_STONE_ITEM.get());
                        output.accept(ru.adaptionwheel.entity.ModSpawnEggs.DISCIPLE_EGG.get());
                    })
                    .build());

    public AdaptionWheel(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, AdaptionConfig.SERVER_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, AdaptionConfig.CLIENT_SPEC);
        AttachmentTypes.ATTACHMENTS.register(modEventBus);
        ru.adaptionwheel.advancement.AdaptationTrigger.TRIGGERS.register(modEventBus);
        ModDataComponents.COMPONENTS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ru.adaptionwheel.menu.ModMenus.register(modEventBus);
        ru.adaptionwheel.effect.ModEffects.EFFECTS.register(modEventBus);
        ru.adaptionwheel.block.ModBlocks.BLOCKS.register(modEventBus);
        ru.adaptionwheel.block.ModBlocks.BLOCK_ITEMS.register(modEventBus);
        ru.adaptionwheel.entity.ModEntities.ENTITIES.register(modEventBus);
        ru.adaptionwheel.entity.ModSpawnEggs.EGGS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            Item item = ModItems.MAHORAGA_WHEEL.get();
            CuriosApi.registerCurio(item, (ICurioItem) item);
            // The stone's price list, resolved now that both item registries are populated.
            ru.adaptionwheel.server.DomainExchange.bootstrap();
        });
    }

    @EventBusSubscriber(modid = MODID)
    public static class ModBusEvents {

        /**
         * Whether an event is about the named vanilla tab.
         *
         * <p>Compared by location rather than by key object. {@code ResourceKey} overrides neither
         * {@code equals} nor {@code hashCode} in 1.21.1 either, so {@code ==} is identity and a
         * hand-built key is never the registry's object — which is why the 26.3 branch's Combat
         * branch had been dead code from the day it was written. Matching on the location works no
         * matter which object the event hands back, and is the only form of this check that is
         * correct by construction rather than by which field happens to be public.</p>
         */
        private static boolean isTab(ResourceKey<CreativeModeTab> key, String id) {
            return key != null && key.location().equals(ResourceLocation.withDefaultNamespace(id));
        }

        @SubscribeEvent
        public static void onRegisterScreens(
                net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
            event.register(ru.adaptionwheel.menu.ModMenus.DOMAIN_STONE.get(),
                    ru.adaptionwheel.client.DomainStoneScreen::new);
        }

        @SubscribeEvent
        public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
            if (isTab(event.getTabKey(), "functional_blocks")) {
                // The ritual set, in the order it becomes useful rather than alphabetically:
                // light it, speed an analysis, raise the buff, then the one that gives.
                event.accept(ru.adaptionwheel.block.ModBlocks.ADAPTATION_BRAZIER_ITEM.get());
                event.accept(ru.adaptionwheel.block.ModBlocks.WHEEL_TOTEM_ITEM.get());
                event.accept(ru.adaptionwheel.block.ModBlocks.RESONANCE_ALTAR_ITEM.get());
                event.accept(ru.adaptionwheel.block.ModBlocks.DOMAIN_STONE_ITEM.get());
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