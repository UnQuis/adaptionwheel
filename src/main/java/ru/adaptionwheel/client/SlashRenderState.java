package ru.adaptionwheel.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.Vec3;

/**
 * Render state shared by the two Sword of Extermination projectile renderers
 * (26.x entity rendering is state-based: the entity is sampled once per frame
 * into this object, and drawing happens from it).
 */
public class SlashRenderState extends EntityRenderState {
    public Vec3 motion = Vec3.ZERO;
    public float roll;
    /** Age including the current partial tick. */
    public float age;
}
