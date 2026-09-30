package ru.adaptionwheel.entity;

import net.minecraft.core.registries.Registries;
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
                    .build("cursed_slash"));

    /**
     * The mod's only mob. Sized like a player so the humanoid model renders correctly, and
     * deliberately a MONSTER category so it spawns in the dark like one and counts for a beacon.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<DiscipleEntity>> DISCIPLE =
            ENTITIES.register("disciple", () -> EntityType.Builder.<DiscipleEntity>of(
                            DiscipleEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(8)
                    .fireImmune()
                    .build("disciple"));

    public static final DeferredHolder<EntityType<?>, EntityType<SpatialRiftProjectile>> SPATIAL_RIFT =
            ENTITIES.register("spatial_rift", () -> EntityType.Builder.<SpatialRiftProjectile>of(
                            SpatialRiftProjectile::new, MobCategory.MISC)
                    .sized(0.6f, 0.6f)
                    .fireImmune()
                    .clientTrackingRange(8)
                    .updateInterval(10)
                    .build("spatial_rift"));
}
