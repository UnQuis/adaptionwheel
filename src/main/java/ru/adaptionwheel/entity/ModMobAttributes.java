package ru.adaptionwheel.entity;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import ru.adaptionwheel.AdaptionWheel;

/**
 * Attribute registration.
 *
 * <p>Its own subscriber for one entry, because the first mob in the mod is not the same as every
 * future mob: the moment a second one appears this is a list again, and putting it in the mod class
 * would mean moving it then.</p>
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID)
public final class ModMobAttributes {

    private ModMobAttributes() {
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.DISCIPLE.get(), DiscipleEntity.createAttributes().build());
    }
}
