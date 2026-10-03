package ru.adaptionwheel.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import ru.adaptionwheel.AdaptionWheel;

@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class ModModelLayers {

    public static final ModelLayerLocation DISCIPLE = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "disciple"), "main");

    private ModModelLayers() {
    }

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {

        event.registerLayerDefinition(DISCIPLE, () -> LayerDefinition.create(
                HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F), 64, 64));
    }
}
