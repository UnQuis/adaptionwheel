package ru.adaptionwheel;

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

    /**
     * Whether an event is about the named vanilla tab.
     *
     * <p>Compared by <em>identifier</em> rather than by key object, and that is the whole point.
     * {@code ResourceKey} overrides neither {@code equals} nor {@code hashCode}, so two keys for
     * the same tab are different objects and {@code ==} is identity. A hand-built key is
     * therefore never equal to the registry's, which is why the Combat branch below had been dead
     * code for the entire life of the mod: it built its own key and compared it by reference.
     * Matching on the identifier works regardless of which object the event hands back.</p>
     */
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
                        // The ritual set, then the mob. In the mod's own tab rather than only in
                        // the vanilla ones, because this lambda is the one place that is
                        // guaranteed to run -- see isTab() for why the event handler is not.
                        output.accept(ru.adaptionwheel.block.ModBlocks.ADAPTATION_BRAZIER_ITEM.get());
                        output.accept(ru.adaptionwheel.block.ModBlocks.WHEEL_TOTEM_ITEM.get());
                        output.accept(ru.adaptionwheel.block.ModBlocks.RESONANCE_ALTAR_ITEM.get());
                        output.accept(ru.adaptionwheel.entity.ModSpawnEggs.DISCIPLE_EGG.get());
                    })
                    .build());

    public AdaptionWheel(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, AdaptionConfig.SERVER_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, AdaptionConfig.CLIENT_SPEC);
        AttachmentTypes.ATTACHMENTS.register(modEventBus);
        ModDataComponents.COMPONENTS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ru.adaptionwheel.menu.ModMenus.register(modEventBus);
        ru.adaptionwheel.effect.ModEffects.EFFECTS.register(modEventBus);
        ru.adaptionwheel.block.ModBlocks.BLOCKS.register(modEventBus);
        ru.adaptionwheel.block.ModBlocks.BLOCK_ITEMS.register(modEventBus);
        // The entity register must come before the spawn-egg register: 26.3's SpawnEggItem reads
        // the mob out of the ENTITY_DATA component written at supplier time, so reversing these
        // two lines makes the boot fail on a missing entity type.
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
            // One screen, one line. There were three registrations here once, one per block with a
            // container, and two screen classes that differed in nothing but the title they printed.
            event.register(ru.adaptionwheel.menu.ModMenus.RESONANCE_ALTAR_TRADE.get(),
                    ru.adaptionwheel.TradeScreen::new);
        }

        @SubscribeEvent
        public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
            // Also mirrored into the mod's own tab, which is the reliable one; these are so the
            // content also sits where a player would look for it.
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
