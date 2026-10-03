package ru.adaptionwheel.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.adaptionwheel.AdaptionWheel;

public final class ModSpawnEggs {

    public static final DeferredRegister<Item> EGGS =
            DeferredRegister.create(Registries.ITEM, AdaptionWheel.MODID);

    public static final DeferredHolder<Item, Item> DISCIPLE_EGG =
            EGGS.register("disciple_spawn_egg", () -> new SpawnEggItem(new Item.Properties()

                    .setId(net.minecraft.resources.ResourceKey.create(Registries.ITEM,
                            net.minecraft.resources.Identifier.fromNamespaceAndPath(
                                    AdaptionWheel.MODID, "disciple_spawn_egg")))
                    .stacksTo(64)

                    .spawnEgg(ModEntities.DISCIPLE.get())));

    private ModSpawnEggs() {
    }
}
