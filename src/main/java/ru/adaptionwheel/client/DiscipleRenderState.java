package ru.adaptionwheel.client;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/**
 * The Disciple's per-frame render state.
 *
 * <p>26.3 moved rendering entirely onto render states: the renderer never sees the entity, it sees
 * this. A mob therefore needs a state class even when it adds nothing of its own, which is why this
 * is an empty subclass rather than a reuse of {@code HumanoidRenderState} — {@code createRenderState}
 * is abstract-returning and a distinct type leaves room to add fields without touching the renderer
 * generics later.</p>
 */
public class DiscipleRenderState extends HumanoidRenderState {
}
