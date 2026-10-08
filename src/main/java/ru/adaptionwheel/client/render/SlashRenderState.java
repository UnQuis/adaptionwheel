package ru.adaptionwheel.client.render;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.Vec3;

/**
 * Render state for {@link CursedSlashProjectile}. Carries only what submit() actually needs beyond
 * the usual position/rotation vanilla already tracks in the base {@link EntityRenderState}: the
 * flight direction (to orient the mesh), the roll (to spin it about its own axis) and the age (to
 * drive the unfurl and fade curves).
 *
 * <p>Fields are snapshotted in {@link CursedSlashRenderer#extractRenderState}, not read live off the
 * entity in {@code submit}, because render state extraction can happen off the entity's own tick
 * thread by design -- this is the whole point of the render-state split.
 */
public class SlashRenderState extends EntityRenderState {
    public Vec3 motion = Vec3.ZERO;
    public float roll;
    public float age;
}