package ru.adaptionwheel.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.adaptionwheel.AdaptionWheel;

/**
 * Spawn eggs for the mod's mobs.
 *
 * <p>A separate register from {@code ModItems} because an egg is not a thing a player crafts or
 * finds, and mixing the two is how a spawn egg ends up in a recipe by accident.</p>
 *
 * <p>26.3 rewrote spawn eggs: {@code NeoForgeSpawnEggItem}/{@code DeferredSpawnEggItem} are gone
 * and vanilla's {@link SpawnEggItem} resolves its mob from the {@code ENTITY_DATA} component
 * instead of a constructor argument, so the type has to be written into the item's properties.
 * That only works because the entity register is attached to the mod bus <em>before</em> this one,
 * so the entity exists by the time this supplier runs — reversing those two lines makes the boot
 * fail with a missing type rather than a subtle runtime no-op.</p>
 */
public final class ModSpawnEggs {

    public static final DeferredRegister<Item> EGGS =
            DeferredRegister.create(Registries.ITEM, AdaptionWheel.MODID);

    public static final DeferredHolder<Item, Item> DISCIPLE_EGG =
            EGGS.register("disciple_spawn_egg", () -> new SpawnEggItem(new Item.Properties()
                    // 26.3 asks the properties for their registry key from inside the Item
                    // constructor, so it has to be set before the item exists. Same requirement
                    // as a BlockItem; a plain new Item.Properties() fails with a bare
                    // "Item id not set" that names neither the item nor the mod.
                    .setId(net.minecraft.resources.ResourceKey.create(Registries.ITEM,
                            net.minecraft.resources.Identifier.fromNamespaceAndPath(
                                    AdaptionWheel.MODID, "disciple_spawn_egg")))
                    .stacksTo(64)
                    // Item.Properties.spawnEgg is vanilla's own helper for this: it writes the
                    // ENTITY_DATA component and the entity's required features in one call.
                    // Building the component by hand is the same thing with more ways to be wrong.
                    .spawnEgg(ModEntities.DISCIPLE.get())));

    private ModSpawnEggs() {
    }
}
