package ru.adaptionwheel.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import ru.adaptionwheel.AdaptionWheel;

/**
 * Model layer locations, and the mesh each one bakes.
 *
 * <p>Its own class because a layer is a name that has to match baked data exactly, and keeping the
 * string next to the mesh that satisfies it is the only way to be sure the two agree.</p>
 *
 * <p>The event is the nested {@code EntityRenderersEvent.RegisterLayerDefinitions}, not a
 * top-level class of its own — the import that looks right does not resolve, and that is the kind
 * of thing worth writing down once. The mesh is generated from vanilla's humanoid builder rather
 * than shipped as baked data, because a Disciple is a person-shaped thing and a hand-authored
 * humanoid is strictly worse than the one the game already draws.</p>
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class ModModelLayers {

    public static final ModelLayerLocation DISCIPLE = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "disciple"), "main");

    private ModModelLayers() {
    }

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        // createMesh gives a MeshDefinition; the event wants a LayerDefinition, which is that
        // mesh plus the texture dimensions it is baked against. 64x64 is the humanoid standard.
        event.registerLayerDefinition(DISCIPLE, () -> LayerDefinition.create(
                HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F), 64, 64));
    }
}
