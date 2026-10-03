package ru.adaptionwheel.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.adaptionwheel.AdaptionWheel;

public final class ModSpawnEggs {

    public static final DeferredRegister<Item> EGGS =
            DeferredRegister.create(Registries.ITEM, AdaptionWheel.MODID);

    public static final DeferredHolder<Item, Item> DISCIPLE_EGG =
            EGGS.register("disciple_spawn_egg", () ->

                    new net.neoforged.neoforge.common.DeferredSpawnEggItem(
                            () -> ModEntities.DISCIPLE.get(), 0xFFB03D8B, 0xFF201830,
                            new Item.Properties()));

    private ModSpawnEggs() {
    }
}
