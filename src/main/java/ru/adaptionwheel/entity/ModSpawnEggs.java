package ru.adaptionwheel.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.adaptionwheel.AdaptionWheel;

/**
 * Spawn eggs for the mod's mobs.
 *
 * <p>A separate register from {@code ModItems} because an egg is not a thing a player crafts or
 * finds, and mixing the two is how a spawn egg ends up in a recipe by accident.</p>
 */
public final class ModSpawnEggs {

    public static final DeferredRegister<Item> EGGS =
            DeferredRegister.create(Registries.ITEM, AdaptionWheel.MODID);

    public static final DeferredHolder<Item, Item> DISCIPLE_EGG =
            EGGS.register("disciple_spawn_egg", () ->
                    // Deferred, not ForgeSpawnEggItem: the entity type does not exist yet while
                    // this supplier is being written, and the spawn egg has to be created after it.
                    new net.neoforged.neoforge.common.DeferredSpawnEggItem(
                            () -> ModEntities.DISCIPLE.get(), 0xFFB03D8B, 0xFF201830,
                            new Item.Properties()));

    private ModSpawnEggs() {
    }
}
