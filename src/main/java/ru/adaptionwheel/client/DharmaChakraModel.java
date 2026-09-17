package ru.adaptionwheel.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.resources.ResourceLocation;
import ru.adaptionwheel.AdaptionWheel;

/**
 * 3D Dharma Chakra wheel model converted from Bedrock Edition dharma_chakra.json.
 * Rendered above the player's head by WheelRenderer.
 * <p>
 * This is a pure-utility class — it does NOT extend EntityModel.
 * The root {@link ModelPart} is obtained via {@link #getBakedRoot()} and rendered
 * directly by WheelRenderer using PoseStack + VertexConsumer.
 */
public final class DharmaChakraModel {

    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "textures/entity/dharma_chakra.png");

    private static ModelPart bakedRoot;

    private DharmaChakraModel() {
    }

    /**
     * Returns a lazily-baked root {@link ModelPart}.
     * Thread-safe enough for the render thread (single-threaded rendering).
     */
    public static ModelPart getBakedRoot() {
        if (bakedRoot == null) {
            bakedRoot = createBodyLayer().bakeRoot();
        }
        return bakedRoot;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        var root = mesh.getRoot();

        // === balls group (9 cubes) ===

        // Element 0: from [5, 0, 4] to [6, 1, 5]
        root.addOrReplaceChild("cube_0",
                CubeListBuilder.create().addBox(0f, 0f, 0f, 1f, 1f, 1f),
                PartPose.offset(-3.0f, 0.0f, -3.0f));

        // Element 1: from [5, 0, 10] to [6, 1, 11]
        root.addOrReplaceChild("cube_1",
                CubeListBuilder.create().addBox(0f, 0f, 0f, 1f, 1f, 1f),
                PartPose.offset(-3.0f, 0.0f, 3.0f));

        // Element 2: from [11, 0, 4] to [12, 1, 5]
        root.addOrReplaceChild("cube_2",
                CubeListBuilder.create().addBox(0f, 0f, 0f, 1f, 1f, 1f),
                PartPose.offset(3.0f, 0.0f, -3.0f));

        // Element 3: from [11, 0, 10] to [12, 1, 11]
        root.addOrReplaceChild("cube_3",
                CubeListBuilder.create().addBox(0f, 0f, 0f, 1f, 1f, 1f),
                PartPose.offset(3.0f, 0.0f, 3.0f));

        // Element 4: from [8, 0, 3] to [9, 1, 4]
        root.addOrReplaceChild("cube_4",
                CubeListBuilder.create().addBox(0f, 0f, 0f, 1f, 1f, 1f),
                PartPose.offset(0.0f, 0.0f, -4.0f));

        // Element 5: from [8, 0, 11] to [9, 1, 12]
        root.addOrReplaceChild("cube_5",
                CubeListBuilder.create().addBox(0f, 0f, 0f, 1f, 1f, 1f),
                PartPose.offset(0.0f, 0.0f, 4.0f));

        // Element 6: from [4, 0, 7] to [5, 1, 8]
        root.addOrReplaceChild("cube_6",
                CubeListBuilder.create().addBox(0f, 0f, 0f, 1f, 1f, 1f),
                PartPose.offset(-4.0f, 0.0f, 0.0f));

        // Element 7: from [12, 0, 7] to [13, 1, 8]
        root.addOrReplaceChild("cube_7",
                CubeListBuilder.create().addBox(0f, 0f, 0f, 1f, 1f, 1f),
                PartPose.offset(4.0f, 0.0f, 0.0f));

        // Element 8: from [8, 0, 7] to [9, 1, 8]
        root.addOrReplaceChild("cube_8",
                CubeListBuilder.create().addBox(0f, 0f, 0f, 1f, 1f, 1f),
                PartPose.offset(0.0f, 0.0f, 0.0f));

        // === rods group (8 cubes, 4 rotated -45deg, 4 unrotated) ===

        // Element 9
        root.addOrReplaceChild("cube_9",
                CubeListBuilder.create().addBox(-1f, 0f, -0.05f, 4f, 0.3f, 0.3f),
                PartPose.offsetAndRotation(-1.6f, 0.3f, -1.6f, 0f, -0.7854f, 0f));

        // Element 10
        root.addOrReplaceChild("cube_10",
                CubeListBuilder.create().addBox(-1f, 0f, -0.05f, 4f, 0.3f, 0.3f),
                PartPose.offsetAndRotation(1.4f, 0.3f, 1.4f, 0f, -0.7854f, 0f));

        // Element 11
        root.addOrReplaceChild("cube_11",
                CubeListBuilder.create().addBox(-0.05f, 0f, -3f, 0.3f, 0.3f, 4f),
                PartPose.offsetAndRotation(1.4f, 0.3f, -0.6f, 0f, -0.7854f, 0f));

        // Element 12
        root.addOrReplaceChild("cube_12",
                CubeListBuilder.create().addBox(-0.05f, 0f, -3f, 0.3f, 0.3f, 4f),
                PartPose.offsetAndRotation(-1.6f, 0.3f, 2.4f, 0f, -0.7854f, 0f));

        // Element 13
        root.addOrReplaceChild("cube_13",
                CubeListBuilder.create().addBox(0f, 0f, -0.05f, 3f, 0.3f, 0.3f),
                PartPose.offset(-3f, 0.3f, 0.4f));

        // Element 14
        root.addOrReplaceChild("cube_14",
                CubeListBuilder.create().addBox(-0.05f, 0f, -3f, 0.3f, 0.3f, 3f),
                PartPose.offset(0.4f, 0.3f, 0f));

        // Element 15
        root.addOrReplaceChild("cube_15",
                CubeListBuilder.create().addBox(-0.05f, 0f, -3f, 0.3f, 0.3f, 3f),
                PartPose.offset(0.4f, 0.3f, 4f));

        // Element 16
        root.addOrReplaceChild("cube_16",
                CubeListBuilder.create().addBox(0f, 0f, -0.05f, 3f, 0.3f, 0.3f),
                PartPose.offset(1f, 0.3f, 0.4f));

        // === ring group (8 cubes, 4 rotated, 4 unrotated) ===

        // Element 17
        root.addOrReplaceChild("cube_17",
                CubeListBuilder.create().addBox(0f, 0f, 0f, 2f, 0.3f, 0.3f),
                PartPose.offsetAndRotation(1.4f, 0.3f, 2.8f, 0f, 0.7854f, 0f));

        // Element 18
        root.addOrReplaceChild("cube_18",
                CubeListBuilder.create().addBox(0f, 0f, 0f, 2f, 0.3f, 0.3f),
                PartPose.offsetAndRotation(-2f, 0.3f, -0.6f, 0f, 0.7854f, 0f));

        // Element 19
        root.addOrReplaceChild("cube_19",
                CubeListBuilder.create().addBox(0f, 0f, 0f, 2f, 0.3f, 0.3f),
                PartPose.offsetAndRotation(-1.8f, 0.3f, 1.4f, 0f, -0.7854f, 0f));

        // Element 20
        root.addOrReplaceChild("cube_20",
                CubeListBuilder.create().addBox(0f, 0f, 0f, 2f, 0.3f, 0.3f),
                PartPose.offsetAndRotation(1.6f, 0.3f, -2f, 0f, -0.7854f, 0f));

        // Element 21
        root.addOrReplaceChild("cube_21",
                CubeListBuilder.create().addBox(0f, 0f, -2f, 0.3f, 0.3f, 2f),
                PartPose.offset(2.7f, 0.3f, 1.5f));

        // Element 22
        root.addOrReplaceChild("cube_22",
                CubeListBuilder.create().addBox(0f, 0f, -2f, 0.3f, 0.3f, 2f),
                PartPose.offset(-2f, 0.3f, 1.5f));

        // Element 23
        root.addOrReplaceChild("cube_23",
                CubeListBuilder.create().addBox(0f, 0f, 0f, 2f, 0.3f, 0.3f),
                PartPose.offset(-0.5f, 0.3f, -2f));

        // Element 24
        root.addOrReplaceChild("cube_24",
                CubeListBuilder.create().addBox(0f, 0f, -0.05f, 2f, 0.3f, 0.3f),
                PartPose.offset(-0.5f, 0.3f, 2.75f));

        return LayerDefinition.create(mesh, 32, 32);
    }
}
