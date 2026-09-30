package ru.adaptionwheel.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import ru.adaptionwheel.entity.DiscipleEntity;

/**
 * The Disciple's body.
 *
 * <p>Vanilla's {@link HumanoidModel} rather than a model of its own. The point of a mob whose
 * strength is read off the player is that you recognise it instantly, and the fastest way to be
 * recognised is to have the same silhouette every other humanoid in the game has.</p>
 */
public class DiscipleModel extends HumanoidModel<DiscipleEntity> {

    public DiscipleModel(ModelPart root) {
        super(root);
    }
}
