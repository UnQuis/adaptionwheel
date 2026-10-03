package ru.adaptionwheel.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.resources.ResourceLocation;
import ru.adaptionwheel.AdaptionWheel;

public final class DharmaChakraModel {

    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "textures/entity/dharma_chakra.png");

    private static ModelPart bakedRoot;

    private DharmaChakraModel() {
    }

    public static ModelPart getBakedRoot() {
        if (bakedRoot == null) {
            bakedRoot = createBodyLayer().bakeRoot();
        }
        return bakedRoot;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        var root = mesh.getRoot();

        root.addOrReplaceChild("cube_0",
                CubeListBuilder.create().addBox(0f, 0f, 0f, 1f, 1f, 1f),
                PartPose.offset(-3.0f, 0.0f, -3.0f));

        root.addOrReplaceChild("cube_1",
                CubeListBuilder.create().addBox(0f, 0f, 0f, 1f, 1f, 1f),
                PartPose.offset(-3.0f, 0.0f, 3.0f));

        root.addOrReplaceChild("cube_2",
                CubeListBuilder.create().addBox(0f, 0f, 0f, 1f, 1f, 1f),
                PartPose.offset(3.0f, 0.0f, -3.0f));

        root.addOrReplaceChild("cube_3",
                CubeListBuilder.create().addBox(0f, 0f, 0f, 1f, 1f, 1f),
                PartPose.offset(3.0f, 0.0f, 3.0f));

        root.addOrReplaceChild("cube_4",
                CubeListBuilder.create().addBox(0f, 0f, 0f, 1f, 1f, 1f),
                PartPose.offset(0.0f, 0.0f, -4.0f));

        root.addOrReplaceChild("cube_5",
                CubeListBuilder.create().addBox(0f, 0f, 0f, 1f, 1f, 1f),
                PartPose.offset(0.0f, 0.0f, 4.0f));

        root.addOrReplaceChild("cube_6",
                CubeListBuilder.create().addBox(0f, 0f, 0f, 1f, 1f, 1f),
                PartPose.offset(-4.0f, 0.0f, 0.0f));

        root.addOrReplaceChild("cube_7",
                CubeListBuilder.create().addBox(0f, 0f, 0f, 1f, 1f, 1f),
                PartPose.offset(4.0f, 0.0f, 0.0f));

        root.addOrReplaceChild("cube_8",
                CubeListBuilder.create().addBox(0f, 0f, 0f, 1f, 1f, 1f),
                PartPose.offset(0.0f, 0.0f, 0.0f));

        root.addOrReplaceChild("cube_9",
                CubeListBuilder.create().addBox(-1f, 0f, -0.05f, 4f, 0.3f, 0.3f),
                PartPose.offsetAndRotation(-1.6f, 0.3f, -1.6f, 0f, -0.7854f, 0f));

        root.addOrReplaceChild("cube_10",
                CubeListBuilder.create().addBox(-1f, 0f, -0.05f, 4f, 0.3f, 0.3f),
                PartPose.offsetAndRotation(1.4f, 0.3f, 1.4f, 0f, -0.7854f, 0f));

        root.addOrReplaceChild("cube_11",
                CubeListBuilder.create().addBox(-0.05f, 0f, -3f, 0.3f, 0.3f, 4f),
                PartPose.offsetAndRotation(1.4f, 0.3f, -0.6f, 0f, -0.7854f, 0f));

        root.addOrReplaceChild("cube_12",
                CubeListBuilder.create().addBox(-0.05f, 0f, -3f, 0.3f, 0.3f, 4f),
                PartPose.offsetAndRotation(-1.6f, 0.3f, 2.4f, 0f, -0.7854f, 0f));

        root.addOrReplaceChild("cube_13",
                CubeListBuilder.create().addBox(0f, 0f, -0.05f, 3f, 0.3f, 0.3f),
                PartPose.offset(-3f, 0.3f, 0.4f));

        root.addOrReplaceChild("cube_14",
                CubeListBuilder.create().addBox(-0.05f, 0f, -3f, 0.3f, 0.3f, 3f),
                PartPose.offset(0.4f, 0.3f, 0f));

        root.addOrReplaceChild("cube_15",
                CubeListBuilder.create().addBox(-0.05f, 0f, -3f, 0.3f, 0.3f, 3f),
                PartPose.offset(0.4f, 0.3f, 4f));

        root.addOrReplaceChild("cube_16",
                CubeListBuilder.create().addBox(0f, 0f, -0.05f, 3f, 0.3f, 0.3f),
                PartPose.offset(1f, 0.3f, 0.4f));

        root.addOrReplaceChild("cube_17",
                CubeListBuilder.create().addBox(0f, 0f, 0f, 2f, 0.3f, 0.3f),
                PartPose.offsetAndRotation(1.4f, 0.3f, 2.8f, 0f, 0.7854f, 0f));

        root.addOrReplaceChild("cube_18",
                CubeListBuilder.create().addBox(0f, 0f, 0f, 2f, 0.3f, 0.3f),
                PartPose.offsetAndRotation(-2f, 0.3f, -0.6f, 0f, 0.7854f, 0f));

        root.addOrReplaceChild("cube_19",
                CubeListBuilder.create().addBox(0f, 0f, 0f, 2f, 0.3f, 0.3f),
                PartPose.offsetAndRotation(-1.8f, 0.3f, 1.4f, 0f, -0.7854f, 0f));

        root.addOrReplaceChild("cube_20",
                CubeListBuilder.create().addBox(0f, 0f, 0f, 2f, 0.3f, 0.3f),
                PartPose.offsetAndRotation(1.6f, 0.3f, -2f, 0f, -0.7854f, 0f));

        root.addOrReplaceChild("cube_21",
                CubeListBuilder.create().addBox(0f, 0f, -2f, 0.3f, 0.3f, 2f),
                PartPose.offset(2.7f, 0.3f, 1.5f));

        root.addOrReplaceChild("cube_22",
                CubeListBuilder.create().addBox(0f, 0f, -2f, 0.3f, 0.3f, 2f),
                PartPose.offset(-2f, 0.3f, 1.5f));

        root.addOrReplaceChild("cube_23",
                CubeListBuilder.create().addBox(0f, 0f, 0f, 2f, 0.3f, 0.3f),
                PartPose.offset(-0.5f, 0.3f, -2f));

        root.addOrReplaceChild("cube_24",
                CubeListBuilder.create().addBox(0f, 0f, -0.05f, 2f, 0.3f, 0.3f),
                PartPose.offset(-0.5f, 0.3f, 2.75f));

        return LayerDefinition.create(mesh, 32, 32);
    }
}
