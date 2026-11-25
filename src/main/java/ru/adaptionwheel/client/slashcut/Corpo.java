package ru.adaptionwheel.client.slashcut;

import org.joml.Vector3f;

import java.util.List;

/**
 * A captured mesh, stored as parallel vertex arrays grouped into quads.
 *
 * <p>Each entry in {@code quads} holds four indices into the arrays. Keeping the arrays flat makes
 * clipping cheap: a cut polygon is just another index list over the same vertices.
 */
public final class Corpo {

    private final Vector3f[] positions;
    private final float[] uvs;
    private final Vector3f[] normals;
    private final int[] colors;
    private final int[] lights;
    private final int[][] quads;

    private Corpo(Vector3f[] positions, float[] uvs, Vector3f[] normals,
                  int[] colors, int[] lights, int[][] quads) {
        this.positions = positions;
        this.uvs = uvs;
        this.normals = normals;
        this.colors = colors;
        this.lights = lights;
        this.quads = quads;
    }

    public static Corpo of(List<Fonte> vertices, List<int[]> quads) {
        int n = vertices.size();
        Vector3f[] positions = new Vector3f[n];
        float[] uvs = new float[n * 2];
        Vector3f[] normals = new Vector3f[n];
        int[] colors = new int[n];
        int[] lights = new int[n];
        for (int i = 0; i < n; i++) {
            Fonte f = vertices.get(i);
            positions[i] = f.pos();
            uvs[i * 2] = f.u();
            uvs[i * 2 + 1] = f.v();
            normals[i] = f.normal();
            colors[i] = f.color();
            lights[i] = f.light();
        }
        return new Corpo(positions, uvs, normals, colors, lights, quads.toArray(new int[0][]));
    }

    public int quadCount() {
        return quads.length;
    }

    public int vertexCount() {
        return positions.length;
    }

    public int[] quad(int index) {
        return quads[index];
    }

    public Fonte vertex(int index) {
        return new Fonte(positions[index], uvs[index * 2], uvs[index * 2 + 1],
                normals[index], colors[index], lights[index]);
    }

    public Fonte[] vertices(int[] indices) {
        Fonte[] result = new Fonte[indices.length];
        for (int i = 0; i < indices.length; i++) {
            result[i] = vertex(indices[i]);
        }
        return result;
    }
}
