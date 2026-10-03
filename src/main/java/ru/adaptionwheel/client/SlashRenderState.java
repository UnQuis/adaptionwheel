package ru.adaptionwheel.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.Vec3;

public class SlashRenderState extends EntityRenderState {
    public Vec3 motion = Vec3.ZERO;
    public float roll;

    public float age;
}
