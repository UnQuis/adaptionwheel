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
 *
 * <p>Parameterised by the render state, not the entity — that is the 26.3 shape. On 1.21.1 this was
 * {@code HumanoidModel<DiscipleEntity>} and the model read the entity directly.</p>
 */
public class DiscipleModel extends HumanoidModel<DiscipleRenderState> {

    public DiscipleModel(ModelPart root) {
        super(root);
    }

    /** The entity type is not needed by the model; the generic parameter cannot simply be dropped. */
    @SuppressWarnings("unused")
    private static final Class<DiscipleEntity> ENTITY = DiscipleEntity.class;
}
