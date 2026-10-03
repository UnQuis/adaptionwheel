package ru.adaptionwheel.entity;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import ru.adaptionwheel.AdaptionWheel;

@EventBusSubscriber(modid = AdaptionWheel.MODID)
public final class ModMobAttributes {

    private ModMobAttributes() {
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.DISCIPLE.get(), DiscipleEntity.createAttributes().build());
    }
}
