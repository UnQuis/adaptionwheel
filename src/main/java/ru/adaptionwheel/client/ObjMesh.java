package ru.adaptionwheel.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
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

    /** Little-endian FRRB v1 header: magic(4), then seven uint32 fields. */
    private static final int HEADER_BYTES = 32;

    /** One layer-0 corner record: pos3 + packed RGBA + data4 + aux4 + uv2, four bytes each. */
    private static final int CORNER_BYTES = 14 * 4;

    /** Layer 5: faceCount + centre xyz + radius + height + reserved. */
    private static final int METADATA_BYTES = 28;

    /**
     * Reads an FRRB v1 model binary (see {@code tools/gen_model_bin.py}). The geometry was centred by
     * the converter, so positions are copied verbatim and radius/height come from the metadata layer
     * — subtracting the centre or recomputing the radius here a second time would shift the model by
     * -2x the centre and break the renderer's scale.
     *
     * <p><b>Every header field and the exact byte count are checked before allocating anything.</b>
     * A corrupt count passed to {@code new float[count * STRIDE]} is an OOM up to hundreds of MB, and
     * the reload listener treats that as "no model" — silent, with no field named in the log.
     *
     * <p>The packed colour is the raw float bit pattern {@code 0xFFFFFFFF} for white, i.e. a NaN.
     * It is only ever reinterpreted with {@link Float#floatToRawIntBits(float)}; float arithmetic or a
     * narrowing cast turns white into garbage or black. The material id is <em>not</em> in those bits
     * (8 bits cannot hold 0.5 exactly): it is {@code data4.x}, copied into the corner's red channel at
     * full float precision, with green/blue taken from the packed bytes.
     */
    public static ObjMesh parseBinary(byte[] bytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (buffer.remaining() < HEADER_BYTES) {
            throw new IOException("FRRB header truncated: " + buffer.remaining() + " of " + HEADER_BYTES + " bytes");
        }

        byte[] magicBytes = new byte[4];
        buffer.get(magicBytes);
        String magic = new String(magicBytes, StandardCharsets.US_ASCII);
        if (!"FRRB".equals(magic)) {
            throw new IOException("bad FRRB magic '" + magic + "', expected 'FRRB'");
        }
        int version = buffer.getInt();
        if (version != 1) {
            throw new IOException("unsupported FRRB version " + version + ", expected 1");
        }
        int layerCount = buffer.getInt();
        if (layerCount != 6) {
            throw new IOException("unsupported FRRB layerCount " + layerCount + ", expected 6");
        }
        int cornerCount = buffer.getInt();
        int indexCount = buffer.getInt();
        int groupCount = buffer.getInt();
        int materialCount = buffer.getInt();
        int byteCount = buffer.getInt();

        if (cornerCount <= 0 || cornerCount % 3 != 0) {
            throw new IOException("invalid FRRB cornerCount " + cornerCount + ": expected a positive multiple of 3");
        }
        if (indexCount != cornerCount) {
            throw new IOException("FRRB indexCount " + indexCount + " does not match cornerCount " + cornerCount);
        }
        if (groupCount < 0 || materialCount < 0) {
            throw new IOException("invalid FRRB groupCount " + groupCount + " / materialCount " + materialCount);
        }
        if (byteCount < HEADER_BYTES || byteCount != bytes.length
                || buffer.remaining() != byteCount - HEADER_BYTES) {
            throw new IOException("FRRB byteCount " + byteCount + " does not match the " + bytes.length
                    + " bytes supplied (" + buffer.remaining() + " remaining after the header)");
        }
        // Fixed-size layers must fit before any allocation; 0xFFFFFFFF corners would otherwise reach
        // the float[] constructor with a wrapped/overflowing length.
        long fixedBytes = (long) CORNER_BYTES * cornerCount + 4L * indexCount
                + 8L * (indexCount / 3) + METADATA_BYTES;
        if (fixedBytes > buffer.remaining()) {
            throw new IOException("FRRB cornerCount " + cornerCount + " needs " + fixedBytes
                    + " bytes, but only " + buffer.remaining() + " remain");
        }

        // The index layer is sequential in our converter: the corners are already in draw order, so
        // there is nothing to gather. The views share the slice but keep independent positions.
        ByteBuffer cornerSlice = buffer.slice();
        // slice() resets the byte order to BIG_ENDIAN -- inheriting it would mirror every float bit
        // and turn 0.5 into 8.8E-44 with no exception and no log line.
        cornerSlice.order(ByteOrder.LITTLE_ENDIAN);
        cornerSlice.limit(CORNER_BYTES * cornerCount);
        FloatBuffer source = cornerSlice.asFloatBuffer();
        float[] corners = new float[cornerCount * STRIDE];
        for (int c = 0; c < cornerCount; c++) {
            int s = c * 14;
            int d = c * STRIDE;
            corners[d] = source.get(s);
            corners[d + 1] = source.get(s + 1);
            corners[d + 2] = source.get(s + 2);
            corners[d + 3] = source.get(s + 12); // uv
            corners[d + 4] = source.get(s + 13);
            corners[d + 5] = source.get(s + 8); // normal (aux4.xyz)
            corners[d + 6] = source.get(s + 9);
            corners[d + 7] = source.get(s + 10);
            corners[d + 8] = source.get(s + 4); // material id (data4.x)
            int rgba = Float.floatToRawIntBits(source.get(s + 3)); // packed colour: raw bits, a NaN for white
            corners[d + 9] = ((rgba >>> 8) & 0xFF) / 255.0f;
            corners[d + 10] = ((rgba >>> 16) & 0xFF) / 255.0f;
        }

        buffer.position(buffer.position() + CORNER_BYTES * cornerCount);
        buffer.position(buffer.position() + 4 * indexCount);
        for (int i = 0; i < groupCount; i++) {
            skipName(buffer, "groupCount");
        }
        for (int i = 0; i < materialCount; i++) {
            skipName(buffer, "materialCount");
        }
        buffer.position(buffer.position() + 8 * (indexCount / 3));

        if (buffer.remaining() < METADATA_BYTES) {
            throw new IOException("FRRB metadata layer truncated: " + buffer.remaining() + " bytes");
        }
        int faceCount = buffer.getInt();
        if (faceCount <= 0) {
            throw new IOException("invalid FRRB faceCount " + faceCount + " in the metadata layer");
        }
        buffer.getInt(); // centre xyz: reporting only, positions are already centred
        buffer.getInt();
        buffer.getInt();
        float radius = buffer.getFloat();
        float height = buffer.getFloat();
        buffer.getInt(); // reserved
        if (!(radius > 0.0f) || !Float.isFinite(radius)) {
            throw new IOException("invalid FRRB radius " + radius + " in the metadata layer");
        }
        if (height < 0.0f || !Float.isFinite(height)) {
            throw new IOException("invalid FRRB height " + height + " in the metadata layer");
        }
        if (buffer.remaining() != 0) {
            throw new IOException("FRRB has " + buffer.remaining() + " trailing bytes beyond byteCount " + byteCount);
        }
        return new ObjMesh(corners, faceCount, radius, height);
    }

    /** Skips one length-prefixed UTF-8 name from layer 2 or 3, with a bounds check. */
    private static void skipName(ByteBuffer buffer, String field) throws IOException {
        if (buffer.remaining() < 2) {
            throw new IOException("FRRB " + field + " name length is truncated");
        }
        int length = buffer.getShort() & 0xFFFF;
        if (buffer.remaining() < length) {
            throw new IOException("FRRB " + field + " name of " + length + " bytes is truncated");
        }
        buffer.position(buffer.position() + length);
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
