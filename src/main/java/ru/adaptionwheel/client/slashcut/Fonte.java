package ru.adaptionwheel.client.slashcut;

import org.joml.Vector3f;

/** One captured vertex: world position, model UV, world normal, colour and packed light. */
public record Fonte(Vector3f pos, float u, float v, Vector3f normal, int color, int light) {

    public Fonte lerp(Fonte other, float t) {
        Vector3f p = new Vector3f(pos).lerp(other.pos, t);
        Vector3f n = new Vector3f(normal).lerp(other.normal, t);
        if (n.lengthSquared() > 1.0e-8f) {
            n.normalize();
        }
        return new Fonte(p, u + (other.u - u) * t, v + (other.v - v) * t,
                n, color, light);
    }
}
