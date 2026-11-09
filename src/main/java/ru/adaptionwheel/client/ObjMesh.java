package ru.adaptionwheel.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A minimal Wavefront OBJ reader for the wheel above the head.
 *
 * <p>Deliberately <em>not</em> NeoForge's {@code ObjLoader}. That one is a baked block/item model
 * loader: it wants a model JSON, a material library and a texture-atlas sprite, and it hands back
 * quads for a {@code BakedModel} — none of which applies to an untextured mesh drawn above an
 * entity. What is needed here is positions, a triangle list and one normal per corner (per-face
 * flat on the faceted torus and beams, per-vertex smooth on the spheres), which is a hundred lines
 * and no model-baking machinery at all.
 *
 * <p>Only {@code v}, {@code vt}, {@code vn} and {@code f} are read, plus the vertex-colour extension
 * Blender writes on {@code v} lines ({@code v x y z r g b}). Object names, materials and smoothing
 * groups are ignored on purpose; when a face supplies a {@code vn} for every corner that normal is
 * used as-is (so the re-authored spheres carry smooth per-vertex normals), otherwise a flat
 * per-face normal is derived from the geometry exactly as before (torus and beams stay flat-shaded).
 *
 * <p><b>The vertex colour is how the artist's material survives the export.</b> The wheel is one
 * mesh made of three Blender materials — a bright gold for the ring and the spheres, a darker
 * bronze for the beams — and a Wavefront OBJ carries a material only by name, pointing at an MTL
 * the game does not read. Blender's vertex-colour export bakes that difference into the mesh, so
 * the two metals arrive as data rather than as a hardcoded list of face ranges in the renderer.
 * A file without colours (the sword's) gets white, which multiplies to "no tint".
 *
 * <p>The mesh is <b>centred on its own bounding box</b> while it is built. The wheel spins around
 * the Y axis and hangs from a point above the player's head, so a model whose origin sits on its
 * bottom face would spin and bob around a point that is not its centre. Baking the offset in means
 * the renderer does not have to know the file's origin.
 *
 * <p>Instances are immutable and safe to share between the render thread and a reload listener.
 */
public final class ObjMesh {

    /** Floats per corner: x, y, z, u, v, nx, ny, nz, r, g, b. */
    public static final int STRIDE = 11;

    /** The colour of a vertex in a file that carries none — "multiply by nothing". */
    private static final float[] WHITE = {1.0f, 1.0f, 1.0f};

    private final float[] corners;
    private final int faceCount;
    private final float radius;
    private final float height;

    /**
     * {@link #corners()} repacked as quads, built on first use. Held so the per-frame draw does not
     * rebuild it; the mesh itself stays immutable.
     */
    private volatile float[] quadCorners;

    private ObjMesh(float[] corners, int faceCount, float radius, float height) {
        this.corners = corners;
        this.faceCount = faceCount;
        this.radius = radius;
        this.height = height;
    }

    /**
     * The triangle soup, {@link #STRIDE} floats per corner, three corners per triangle.
     * Positions are in blocks and already centred; normals are unit length and per face.
     * <b>Read-only</b> — the array is shared, and cloning it every frame would defeat the point.
     */
    public float[] corners() {
        return this.corners;
    }

    /**
     * The same soup repacked as <b>quads</b>, {@link #STRIDE} floats per corner, four corners per
     * triangle, with the fourth corner duplicating the third.
     *
     * <p>That duplication is not an optimisation, it is a requirement: the renderer draws through
     * {@code RenderType.entityCutoutNoCull}, whose vertex mode is {@code QUADS}. Feeding it three
     * vertices per triangle makes it group the stream four at a time — the last vertex of one
     * triangle and the first of the next become one quad — so the mesh draws as slivers that cover
     * most of the surface but leave the rest missing. It looks like the wheel is there, with holes
     * in the thin parts and a shimmer over the rest, and nothing throws: no GL error, no log line,
     * just a subtly wrong model. A degenerate quad (a triangle plus a repeated corner) is what a
     * {@code QUADS} buffer needs to represent a triangle exactly.
     *
     * <p><b>Read-only</b>, like {@link #corners()}.
     */
    public float[] quadCorners() {
        float[] quads = this.quadCorners;
        if (quads == null) {
            int triangles = this.corners.length / (STRIDE * 3);
            quads = new float[triangles * 4 * STRIDE];
            int out = 0;
            for (int i = 0; i < this.corners.length; i += STRIDE * 3) {
                for (int corner = 0; corner < 4; corner++) {
                    System.arraycopy(this.corners, i + Math.min(corner, 2) * STRIDE, quads, out, STRIDE);
                    out += STRIDE;
                }
            }
            this.quadCorners = quads;
        }
        return quads;
    }

    /** Faces as they appear in the file, before n-gons are split into triangles. */
    public int faceCount() {
        return this.faceCount;
    }

    /** Triangles in the mesh. Always at least {@link #faceCount()}: every face yields one or more. */
    public int triangleCount() {
        return this.corners.length / (STRIDE * 3);
    }

    /** Horizontal distance from the centre to the farthest vertex, in blocks. */
    public float radius() {
        return this.radius;
    }

    /**
     * Vertical extent of the mesh, in model units.
     *
     * <p>Unlike {@link #radius()} this is not about a bounding circle: the sword's mesh is centred
     * like every other one, but a blade has to be placed by its <b>base</b>, so the renderer needs
     * the half-height to move the centred base back to the origin.
     */
    public float height() {
        return this.height;
    }

    /**
     * Reads an OBJ. Malformed faces are skipped rather than thrown on — a bad line should cost one
     * triangle, not the whole wheel — but a file with no usable vertices at all is an error.
     */
    public static ObjMesh parse(BufferedReader reader) throws IOException {
        List<float[]> positions = new ArrayList<>();
        List<float[]> colours = new ArrayList<>();
        List<float[]> uvs = new ArrayList<>();
        List<float[]> normals = new ArrayList<>();
        List<int[]> triangles = new ArrayList<>();
        int faces = 0;

        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }

            String[] tokens = line.split("\\s+");
            switch (tokens[0]) {
                case "v" -> {
                    if (tokens.length >= 4) {
                        positions.add(new float[]{number(tokens[1]), number(tokens[2]), number(tokens[3])});
                        colours.add(tokens.length >= 7
                                ? new float[]{number(tokens[4]), number(tokens[5]), number(tokens[6])}
                                : WHITE);
                    }
                }
                case "vt" -> {
                    if (tokens.length >= 3) {
                        uvs.add(new float[]{number(tokens[1]), number(tokens[2])});
                    }
                }
                case "vn" -> {
                    if (tokens.length >= 4) {
                        normals.add(new float[]{number(tokens[1]), number(tokens[2]), number(tokens[3])});
                    }
                }
                case "f" -> {
                    if (readFace(tokens, positions.size(), uvs.size(), normals.size(), triangles)) {
                        faces++;
                    }
                }
                default -> {
                    // o, g, s, usemtl, mtllib: ignored.
                }
            }
        }

        if (positions.isEmpty() || triangles.isEmpty()) {
            throw new IOException("no triangles in the OBJ (" + positions.size() + " vertices, " + faces + " faces)");
        }

        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (float[] p : positions) {
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            minZ = Math.min(minZ, p[2]);
            maxX = Math.max(maxX, p[0]);
            maxY = Math.max(maxY, p[1]);
            maxZ = Math.max(maxZ, p[2]);
        }
        float cx = (minX + maxX) * 0.5f;
        float cy = (minY + maxY) * 0.5f;
        float cz = (minZ + maxZ) * 0.5f;

        float radius = 0.0f;
        for (float[] p : positions) {
            radius = Math.max(radius, (float) Math.hypot(p[0] - cx, p[2] - cz));
        }
        if (radius < 1.0e-4f) {
            // The renderer divides by this to size the wheel, so a mesh with no horizontal extent
            // is not a wheel that draws too small -- it is an infinite scale and nothing on screen.
            throw new IOException("the mesh has no horizontal extent");
        }

        float[] corners = new float[triangles.size() * 3 * STRIDE];
        int out = 0;
        for (int[] triangle : triangles) {
            // Triangle layout: [p0, uv0, n0, p1, uv1, n1, p2, uv2, n2]; n is -1 when the file gave
            // no normal (torus/beams) and the geometric flat normal should be used instead.
            float[] flatNormal = null;
            boolean anySmooth = triangle[2] >= 0 || triangle[5] >= 0 || triangle[8] >= 0;
            if (!anySmooth) {
                flatNormal = faceNormal(positions.get(triangle[0]), positions.get(triangle[3]), positions.get(triangle[6]));
            }
            for (int corner = 0; corner < 3; corner++) {
                int pi = triangle[corner * 3];
                int ui = triangle[corner * 3 + 1];
                int ni = triangle[corner * 3 + 2];
                float[] position = positions.get(pi);
                float[] colour = colours.get(pi);
                float[] normal = ni >= 0
                        ? normals.get(ni)
                        : (flatNormal != null ? flatNormal : faceNormal(positions.get(triangle[0]), positions.get(triangle[3]), positions.get(triangle[6])));

                corners[out++] = position[0] - cx;
                corners[out++] = position[1] - cy;
                corners[out++] = position[2] - cz;
                corners[out++] = ui >= 0 ? uvs.get(ui)[0] : 0.0f;
                corners[out++] = ui >= 0 ? uvs.get(ui)[1] : 0.0f;
                corners[out++] = normal[0];
                corners[out++] = normal[1];
                corners[out++] = normal[2];
                corners[out++] = colour[0];
                corners[out++] = colour[1];
                corners[out++] = colour[2];
            }
        }

        return new ObjMesh(corners, faces, radius, maxY - minY);
    }

    /**
     * Triangulates one {@code f} line as a fan and appends it. Returns {@code false} if the line is
     * unusable, which is how a single bad face stays a single lost triangle.
     *
     * <p>Each output triangle is laid out as {@code [p0, u0, n0, p1, u1, n1, p2, u2, n2]} where
     * {@code n} is {@code -1} when the corner did not carry a {@code vn} (so the caller fills in a
     * flat per-face normal) and a valid index into {@code normals} otherwise. This lets a file mix
     * smooth-shaded parts (the spheres, re-authored with per-vertex normals) and flat-shaded parts
     * (the torus and the beams, which look wrong with smooth normals).
     */
    private static boolean readFace(String[] tokens, int positionCount, int uvCount, int normalCount, List<int[]> out) {
        int count = tokens.length - 1;
        if (count < 3) {
            return false;
        }

        int[] position = new int[count];
        int[] uv = new int[count];
        int[] normal = new int[count];
        for (int i = 0; i < count; i++) {
            String[] parts = tokens[i + 1].split("/", -1);
            position[i] = resolve(parts[0], positionCount);
            uv[i] = parts.length > 1 && !parts[1].isEmpty() ? resolve(parts[1], uvCount) : -1;
            normal[i] = parts.length > 2 && !parts[2].isEmpty() ? resolve(parts[2], normalCount) : -1;
            if (position[i] < 0) {
                return false;
            }
        }

        for (int i = 1; i + 1 < count; i++) {
            out.add(new int[]{
                    position[0], uv[0], normal[0],
                    position[i], uv[i], normal[i],
                    position[i + 1], uv[i + 1], normal[i + 1]});
        }
        return true;
    }

    /**
     * OBJ indices are 1-based, and a negative index counts backwards from the end of the list as it
     * stood when the face was read. Returns {@code -1} for anything out of range.
     */
    private static int resolve(String token, int count) {
        int index;
        try {
            index = Integer.parseInt(token);
        } catch (NumberFormatException e) {
            return -1;
        }

        int resolved = index > 0 ? index - 1 : count + index;
        return resolved >= 0 && resolved < count ? resolved : -1;
    }

    private static float number(String token) {
        return Float.parseFloat(token);
    }

    /**
     * The geometric (flat) normal for a triangle, used for faces that do not carry per-vertex
     * {@code vn} normals in the file — the torus and the bronze beams, whose faceted look is
     * part of the design. The spheres carry smooth per-vertex normals in the OBJ and are read
     * straight from the file. A degenerate face (two identical corners, which a fan can produce
     * at a repeated vertex) gets a straight-up normal instead of a division by zero — a NaN here
     * poisons the whole vertex buffer and the wheel disappears with no error anywhere.
     *
     * <p><b>The corner order is the winding, and getting it wrong lights the mesh from below.</b>
     * Blender writes faces counter-clockwise seen from outside, so {@code (a, b, c)} in that order
     * gives the outward normal. This method used to take the corners as {@code (a, c, b)} — the
     * fan's own order was fine, the call site swapped two of them — which negated every normal on
     * every mesh drawn through here. Nothing failed: with a light from above and a 0.55 ambient
     * floor the wheel simply read as a gold ring lit from underneath, which looks like a stylistic
     * choice until you put a specular highlight on it, and then the highlight lands on the wrong
     * side and the whole thing looks hollow.
     */
    private static float[] faceNormal(float[] a, float[] b, float[] c) {
        float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
        float vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0e-6f) {
            return new float[]{0.0f, 1.0f, 0.0f};
        }
        return new float[]{nx / length, ny / length, nz / length};
    }
}
