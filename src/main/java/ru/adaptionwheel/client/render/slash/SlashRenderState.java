package ru.adaptionwheel.client.render.slash;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.Vec3;

/** Render snapshot shared by both slash projectile renderers. */
public class SlashRenderState extends EntityRenderState {
    /** The projectile direction used to orient the mesh. */
    public Vec3 motion = Vec3.ZERO;
    /** Rotation about the projectile's own flight axis. */
    public float roll;
    /** Projectile age in ticks, including render partial tick. */
    public float age;
}
