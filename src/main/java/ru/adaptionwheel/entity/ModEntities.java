package ru.adaptionwheel.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.adaptionwheel.AdaptionWheel;

public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, AdaptionWheel.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<CursedSlashProjectile>> CURSED_SLASH =
            ENTITIES.register("cursed_slash", () -> EntityType.Builder.<CursedSlashProjectile>of(
                            CursedSlashProjectile::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(6)
                    .updateInterval(20)
                    .build(key("cursed_slash")));

    public static final DeferredHolder<EntityType<?>, EntityType<SpatialRiftProjectile>> SPATIAL_RIFT =
            ENTITIES.register("spatial_rift", () -> EntityType.Builder.<SpatialRiftProjectile>of(
                            SpatialRiftProjectile::new, MobCategory.MISC)
                    .sized(0.6f, 0.6f)
                    .fireImmune()
                    .clientTrackingRange(8)
                    .updateInterval(10)
                    .build(key("spatial_rift")));

    private static ResourceKey<EntityType<?>> key(String name) {
        return ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, name));
    }
}
