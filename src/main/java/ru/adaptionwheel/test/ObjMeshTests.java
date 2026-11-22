package ru.adaptionwheel.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.client.ObjMesh;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Pins the OBJ reader the wheel above the head depends on.
 *
 * <p>Every failure here is silent in the game: a missing resource, a face line the parser skips, a
 * division by a zero radius and a NaN normal all end in "the wheel is not drawn" or "the wheel is
 * the old shape", with the only evidence in a log line nobody reads. The renderer falls back to the
 * old 25-cube model on any of them, which is exactly why a test has to assert the OBJ itself.
 *
 * <p>The counts are deliberately loose (a floor, not an equality): the model is an art asset that
 * will be replaced, and a test that has to be edited every time the artist moves a sphere teaches
 * people to edit the test without reading it. The floor still catches the two real parser bugs —
 * quads silently dropped (only one triangle per face) and every face dropped (no triangles at all).
 */
@GameTestHolder(AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class ObjMeshTests {

    private static final String WHEEL_OBJ = "/assets/adaptionwheel/models/entity/dharma_chakra.bin";
    private static final String ORBITAL_OBJ = "/assets/adaptionwheel/models/entity/orbital_ring.bin";

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theShippedWheelParses(GameTestHelper helper) {
        ObjMesh mesh = shipped();

        helper.assertTrue(mesh != null,
                "the wheel OBJ is not readable from the classpath at " + WHEEL_OBJ
                        + " -- the renderer would silently fall back to the old 25-cube model");
        if (mesh == null) {
            return;
        }

        helper.assertTrue(mesh.faceCount() >= 1000,
                "the shipped wheel has at least a thousand faces; parsed " + mesh.faceCount()
                        + ", which means the parser stopped early");
        helper.assertTrue(mesh.triangleCount() >= mesh.faceCount(),
                "every face must yield at least one triangle: " + mesh.faceCount() + " faces became "
                        + mesh.triangleCount() + " triangles, so n-gons are being dropped");
        helper.assertTrue(mesh.radius() > 0.4f && mesh.radius() < 0.9f,
                "the wheel is authored at roughly 0.66 blocks radius; parsed " + mesh.radius()
                        + ", which would size it wrongly against wheelModelScale");

        helper.succeed();
    }

    /**
     * The model's own origin sits on its bottom face (it spans y 0.53..0.72 in the file). The mesh
     * has to be centred on its bounding box while it is built, or the wheel spins and bobs around a
     * point below its centre — which is subtle in motion and impossible to spot in a still frame.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theMeshIsCentredOnItsBoundingBox(GameTestHelper helper) {
        ObjMesh mesh = shipped();
        helper.assertTrue(mesh != null, "the wheel OBJ must load before its centring can be checked");
        if (mesh == null) {
            return;
        }

        float[] corners = mesh.corners();
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < corners.length; i += ObjMesh.STRIDE) {
            minX = Math.min(minX, corners[i]);
            minY = Math.min(minY, corners[i + 1]);
            minZ = Math.min(minZ, corners[i + 2]);
            maxX = Math.max(maxX, corners[i]);
            maxY = Math.max(maxY, corners[i + 1]);
            maxZ = Math.max(maxZ, corners[i + 2]);
        }

        helper.assertTrue(Math.abs(minX + maxX) < 1.0e-3f,
                "the mesh must be centred on x, but its bounds sum to " + (minX + maxX));
        helper.assertTrue(Math.abs(minY + maxY) < 1.0e-3f,
                "the mesh must be centred on y, but its bounds sum to " + (minY + maxY)
                        + " -- the file's origin is on its bottom face");
        helper.assertTrue(Math.abs(minZ + maxZ) < 1.0e-3f,
                "the mesh must be centred on z, but its bounds sum to " + (minZ + maxZ));

        helper.succeed();
    }

    /** OBJ's negative indices count backwards from the list as it stood at that line. */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void negativeIndicesResolve(GameTestHelper helper) {
        ObjMesh mesh = parse("""
                v 0 0 0
                v 1 0 0
                v 1 0 1
                v 0 0 1
                f -4 -3 -2 -1
                """);

        helper.assertTrue(mesh != null, "a quad with negative indices must parse");
        if (mesh == null) {
            return;
        }
        helper.assertTrue(mesh.faceCount() == 1, "one f line is one face, got " + mesh.faceCount());
        helper.assertTrue(mesh.triangleCount() == 2,
                "a quad must become two triangles, got " + mesh.triangleCount());

        helper.succeed();
    }

    /**
     * A fan triangulation can emit a zero-area triangle when a face repeats a vertex. Its normal is
     * a division by zero; one NaN in the vertex buffer takes the whole wheel off the screen with no
     * error, so the degenerate case has to produce a usable normal instead.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void degenerateFacesStayFinite(GameTestHelper helper) {
        ObjMesh mesh = parse("""
                v 0 0 0
                v 1 0 0
                v 1 0 0
                f 1 2 3
                v 0 0 1
                f 1 2 4
                """);

        helper.assertTrue(mesh != null, "a degenerate face must not take the whole file down");
        if (mesh == null) {
            return;
        }

        for (float value : mesh.corners()) {
            helper.assertTrue(Float.isFinite(value),
                    "a degenerate face produced a non-finite value (" + value + "); the wheel would vanish");
        }

        helper.succeed();
    }

    /**
     * The draw path feeds {@code RenderType.entityCutoutNoCull}, whose vertex mode is
     * {@code QUADS}. Three vertices per triangle therefore do not mean "a triangle" to the GPU —
     * they mean "three quarters of a quad", and it groups the stream four at a time across triangle
     * boundaries. The result is a mesh that is mostly there, with slivers where the thin parts
     * (the torus, the beams) should be and a shimmer over the rest. Nothing throws.
     *
     * <p>So each triangle is emitted as a degenerate quad, and this pins the two things that make
     * that work: four corners per triangle, and the fourth one identical to the third.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void quadsPadEveryTriangleWithARepeatedCorner(GameTestHelper helper) {
        ObjMesh mesh = parse("""
                v 0 0 0
                v 1 0 0
                v 1 0 1
                v 0 0 1
                f 1 2 3 4
                """);

        helper.assertTrue(mesh != null, "a quad must parse before its packing can be checked");
        if (mesh == null) {
            return;
        }

        float[] triangles = mesh.corners();
        float[] quads = mesh.quadCorners();
        int trianglesInMesh = mesh.triangleCount();

        helper.assertTrue(quads.length == trianglesInMesh * 4 * ObjMesh.STRIDE,
                "a QUADS buffer needs four corners per triangle: " + trianglesInMesh
                        + " triangles produced " + quads.length + " floats, expected "
                        + (trianglesInMesh * 4 * ObjMesh.STRIDE));
        helper.assertTrue(mesh.quadCorners() == quads,
                "the quad view is built once and cached; rebuilding it per frame is the cost this avoids");

        for (int triangle = 0; triangle < trianglesInMesh; triangle++) {
            int triBase = triangle * 3 * ObjMesh.STRIDE;
            int quadBase = triangle * 4 * ObjMesh.STRIDE;
            for (int corner = 0; corner < 3; corner++) {
                for (int element = 0; element < ObjMesh.STRIDE; element++) {
                    float expected = triangles[triBase + corner * ObjMesh.STRIDE + element];
                    float actual = quads[quadBase + corner * ObjMesh.STRIDE + element];
                    helper.assertTrue(expected == actual,
                            "triangle " + triangle + " corner " + corner + " element " + element
                                    + " must survive the repack: " + expected + " became " + actual);
                }
            }
            for (int element = 0; element < ObjMesh.STRIDE; element++) {
                float third = quads[quadBase + 2 * ObjMesh.STRIDE + element];
                float fourth = quads[quadBase + 3 * ObjMesh.STRIDE + element];
                helper.assertTrue(third == fourth,
                        "the fourth corner must repeat the third, or the quad is not degenerate and "
                                + "the GPU draws a sliver: element " + element + " is " + third + " vs " + fourth);
            }
        }

        helper.succeed();
    }

    /**
     * The wheel's metal now lives in its texture — the Blender file's material multiplies a 4K
     * corroded-metal albedo by gold — so what the mesh has to carry is <b>UVs</b>.
     *
     * <p>This is the silent one: a mesh with no UVs samples texel (0,0) on every face, so the wheel
     * draws as a single flat colour, which is exactly what the old untextured wheel looked like.
     * Nobody would report it as a bug; it would just be a wheel that never got the new texture.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theShippedWheelCarriesTextureCoordinates(GameTestHelper helper) {
        ObjMesh mesh = shipped();
        helper.assertTrue(mesh != null, "the wheel OBJ must load before its UVs can be checked");
        if (mesh == null) {
            return;
        }

        float[] corners = mesh.corners();
        float minU = Float.POSITIVE_INFINITY, maxU = Float.NEGATIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY, maxV = Float.NEGATIVE_INFINITY;
        int distinctNormals = 0;
        float firstNx = corners[5], firstNy = corners[6], firstNz = corners[7];
        for (int i = 0; i < corners.length; i += ObjMesh.STRIDE) {
            minU = Math.min(minU, corners[i + 3]);
            maxU = Math.max(maxU, corners[i + 3]);
            minV = Math.min(minV, corners[i + 4]);
            maxV = Math.max(maxV, corners[i + 4]);
            float nx = corners[i + 5], ny = corners[i + 6], nz = corners[i + 7];
            helper.assertTrue(Math.abs(nx * nx + ny * ny + nz * nz - 1.0f) < 1.0e-3f,
                    "a normal that is not unit length skews the diffuse and specular terms: " + nx + ", " + ny + ", " + nz);
            if (Math.abs(nx - firstNx) > 1.0e-3f || Math.abs(ny - firstNy) > 1.0e-3f || Math.abs(nz - firstNz) > 1.0e-3f) {
                distinctNormals++;
            }
        }

        helper.assertTrue(maxU - minU > 0.5f && maxV - minV > 0.5f,
                "the wheel's UVs span only " + (maxU - minU) + " x " + (maxV - minV) + "; the parts are "
                        + "authored over the full 0..1 tile, so a collapsed range means the export dropped them "
                        + "and the whole wheel samples one texel");
        helper.assertTrue(distinctNormals > 0,
                "every corner shares one normal, so the spheres cannot be smooth -- the file lost its vn lines");

        helper.succeed();
    }

    /** A file with no colours is the sword's: it must multiply to nothing, not to black. */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aFileWithoutColoursIsWhite(GameTestHelper helper) {
        ObjMesh mesh = parse("""
                v 0 0 0
                v 1 0 0
                v 1 0 1
                v 0 0 1
                f 1 2 3 4
                """);

        helper.assertTrue(mesh != null, "a plain quad must parse");
        if (mesh == null) {
            return;
        }
        for (int i = 0; i < mesh.corners().length; i += ObjMesh.STRIDE) {
            helper.assertTrue(mesh.corners()[i + 8] == 1.0f
                            && mesh.corners()[i + 9] == 1.0f
                            && mesh.corners()[i + 10] == 1.0f,
                    "a corner with no colour in the file must be white, or every untextured mesh drawn "
                            + "through this reader turns black");
        }

        helper.succeed();
    }

    /**
     * Blender writes faces counter-clockwise seen from outside, and the renderer derives its flat
     * normal from that winding rather than from the file's {@code vn}. Taking the corners in the
     * wrong order negates every normal on every mesh: the wheel is then lit from below, and with a
     * specular highlight added it glints on the underside. Nothing throws and nothing logs — it
     * just looks like an artistic choice, which is why the convention needs a test of its own.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void faceNormalsPointOutwards(GameTestHelper helper) {
        ObjMesh mesh = parse("""
                v -1 -1 -1
                v  1 -1 -1
                v  1 -1  1
                v -1 -1  1
                v -1  1 -1
                v  1  1 -1
                v  1  1  1
                v -1  1  1
                f 1 2 3 4
                f 5 8 7 6
                f 1 5 6 2
                f 4 3 7 8
                f 1 4 8 5
                f 2 6 7 3
                """);

        helper.assertTrue(mesh != null, "a cube must parse");
        if (mesh == null) {
            return;
        }

        float[] corners = mesh.corners();
        for (int i = 0; i < corners.length; i += ObjMesh.STRIDE * 3) {
            float nx = corners[i + 5], ny = corners[i + 6], nz = corners[i + 7];
            float cx = (corners[i] + corners[i + ObjMesh.STRIDE] + corners[i + 2 * ObjMesh.STRIDE]) / 3.0f;
            float cy = (corners[i + 1] + corners[i + 1 + ObjMesh.STRIDE] + corners[i + 1 + 2 * ObjMesh.STRIDE]) / 3.0f;
            float cz = (corners[i + 2] + corners[i + 2 + ObjMesh.STRIDE] + corners[i + 2 + 2 * ObjMesh.STRIDE]) / 3.0f;
            float dot = nx * cx + ny * cy + nz * cz;
            helper.assertTrue(dot > 0.0f,
                    "the normal of the face centred at (" + cx + ", " + cy + ", " + cz + ") points "
                            + "towards the middle of the cube: " + dot + ". The winding is being read "
                            + "backwards, so every mesh drawn through this reader is lit from inside.");
        }

        helper.succeed();
    }

    /**
     * The orbital skin is one mesh exported from three Blender materials. An OBJ has nowhere to put
     * a material the game reads, so each object carries its material id in the red channel of its
     * vertex colour (0 / 0.5 / 1); green and blue stay zero. The renderer will key off that id, so a
     * dropped colour attribute would collapse all three rings into one material with no error
     * anywhere. Pin the ids at the source, along with the mesh's size and finite normals.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theOrbitalWheelParsesWithMaterialIds(GameTestHelper helper) {
        ObjMesh mesh = load(ORBITAL_OBJ);
        helper.assertTrue(mesh != null,
                "orbital OBJ is not readable from the classpath at " + ORBITAL_OBJ
                        + " -- the skin renderer would silently draw nothing");
        if (mesh == null) {
            return;
        }

        helper.assertTrue(mesh.triangleCount() >= 50000,
                "the orbital ring includes the realised Geometry Nodes detail (~59k triangles); parsed "
                        + mesh.triangleCount() + ", which means a re-export dropped the Bezier-circle instances "
                        + "and left only the three plain toruses");
        helper.assertTrue(mesh.radius() > 0,
                "the orbital OBJ must have a horizontal extent, but its radius is " + mesh.radius());

        boolean found0 = false, foundHalf = false, found1 = false;
        float[] corners = mesh.corners();
        for (int i = 0; i < corners.length; i += ObjMesh.STRIDE) {
            float nx = corners[i + 5], ny = corners[i + 6], nz = corners[i + 7];
            helper.assertTrue(Float.isFinite(nx) && Float.isFinite(ny) && Float.isFinite(nz),
                    "an orbital corner has a non-finite normal: " + nx + ", " + ny + ", " + nz);
            float r = corners[i + 8], g = corners[i + 9], b = corners[i + 10];
            helper.assertTrue(g == 0.0f && b == 0.0f,
                    "the material id lives in red only; found green " + g + " and blue " + b);
            if (Math.abs(r - 0.0f) < 0.01f) {
                found0 = true;
            } else if (Math.abs(r - 0.5f) < 0.01f) {
                foundHalf = true;
            } else if (Math.abs(r - 1.0f) < 0.01f) {
                found1 = true;
            } else {
                helper.assertTrue(false, "an orbital corner has material id " + r
                        + ", expected one of 0, 0.5 or 1");
            }
        }
        helper.assertTrue(found0 && foundHalf && found1,
                "all three material ids must survive the export; found 0=" + found0
                        + " 0.5=" + foundHalf + " 1=" + found1);

        // The original wheel has no colour attribute in the file, so it must still read as white.
        ObjMesh dharma = shipped();
        helper.assertTrue(dharma != null, "the shipped wheel must load before its colours can be re-checked");
        if (dharma != null) {
            for (int i = 0; i < dharma.corners().length; i += ObjMesh.STRIDE) {
                float r = dharma.corners()[i + 8], g = dharma.corners()[i + 9], b = dharma.corners()[i + 10];
                helper.assertTrue(r == 1.0f && g == 1.0f && b == 1.0f,
                        "the shipped wheel carries no vertex colours, so every corner must be white; got "
                                + r + ", " + g + ", " + b);
            }
        }

        helper.succeed();
    }

    /**
     * The orbital shader keys every shell off the red vertex-colour channel. The draw path reads the
     * QUADS view, not {@code corners()}: if a future {@code STRIDE} change shifted or dropped that
     * channel during repacking, every shell would silently draw as the same material with no error.
     * Pin the ids on the array that actually reaches the GPU, including the duplicated fourth corner.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theOrbitalIdsSurviveQuadPacking(GameTestHelper helper) {
        ObjMesh mesh = load(ORBITAL_OBJ);
        helper.assertTrue(mesh != null, "the orbital OBJ must load before its quad packing can be checked");
        if (mesh == null) {
            return;
        }

        float[] triangles = mesh.corners();
        float[] quads = mesh.quadCorners();
        boolean found0 = false, foundHalf = false, found1 = false;
        int lost = 0, repeated = 0;
        for (int triangle = 0; triangle < mesh.triangleCount(); triangle++) {
            int triBase = triangle * 3 * ObjMesh.STRIDE;
            int quadBase = triangle * 4 * ObjMesh.STRIDE;
            for (int corner = 0; corner < 3; corner++) {
                float expected = triangles[triBase + corner * ObjMesh.STRIDE + 8];
                float actual = quads[quadBase + corner * ObjMesh.STRIDE + 8];
                if (Math.abs(expected - actual) > 0.001f) {
                    lost++;
                }
            }
            if (quads[quadBase + 2 * ObjMesh.STRIDE + 8] != quads[quadBase + 3 * ObjMesh.STRIDE + 8]) {
                repeated++;
            }
            for (int corner = 0; corner < 4; corner++) {
                float id = quads[quadBase + corner * ObjMesh.STRIDE + 8];
                if (Math.abs(id - 0.0f) < 0.01f) {
                    found0 = true;
                } else if (Math.abs(id - 0.5f) < 0.01f) {
                    foundHalf = true;
                } else if (Math.abs(id - 1.0f) < 0.01f) {
                    found1 = true;
                } else {
                    lost++;
                }
            }
        }

        helper.assertTrue(lost == 0,
                "quad packing changed or dropped the material id on " + lost + " corners; every shell "
                        + "would draw as the same material");
        helper.assertTrue(repeated == 0,
                "the fourth corner must duplicate the third on all " + mesh.triangleCount()
                        + " triangles, but " + repeated + " differ");
        helper.assertTrue(found0 && foundHalf && found1,
                "quads must still carry all three ids; found 0=" + found0 + " 0.5=" + foundHalf
                        + " 1=" + found1);

        helper.succeed();
    }

    /**
     * The converter must produce the same mesh the text parser does. The fixture is small enough to
     * compare every float: one centred quad, two triangles, two UVs and a 7-token colour line whose
     * red (the material id) is 1.0. Everything except the material id is compared with a 1e-6
     * tolerance; the material id is exact because the shader buckets it.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void binaryMatchesTextParserOnFixture(GameTestHelper helper) {
        ObjMesh text = loadText("/models/fixture.obj");
        byte[] binary = bytes("/models/fixture.bin");
        helper.assertTrue(text != null && binary != null,
                "the FRRB fixture resources (/models/fixture.obj and .bin) must be on the test classpath");
        if (text == null || binary == null) {
            return;
        }

        ObjMesh parsed;
        try {
            parsed = ObjMesh.parseBinary(binary);
        } catch (IOException e) {
            helper.assertTrue(false, "the valid FRRB fixture must parse: " + e.getMessage());
            return;
        }

        helper.assertTrue(parsed != null, "parseBinary returned null for a valid fixture");
        if (parsed == null) {
            return;
        }
        helper.assertTrue(parsed.triangleCount() == text.triangleCount(),
                "binary has " + parsed.triangleCount() + " triangles, text has " + text.triangleCount());
        helper.assertTrue(parsed.faceCount() == text.faceCount(),
                "binary has " + parsed.faceCount() + " faces, text has " + text.faceCount());
        helper.assertTrue(Math.abs(parsed.radius() - text.radius()) < 1.0e-6f
                        && Math.abs(parsed.height() - text.height()) < 1.0e-6f,
                "radius/height must come from the metadata layer, not be recomputed: "
                        + parsed.radius() + "/" + parsed.height() + " vs " + text.radius() + "/" + text.height());

        float[] expected = text.corners();
        float[] actual = parsed.corners();
        helper.assertTrue(expected.length == actual.length, "corner arrays differ in length");
        for (int i = 0; i < Math.min(expected.length, actual.length); i += ObjMesh.STRIDE) {
            for (int element = 0; element < 8; element++) {
                helper.assertTrue(Math.abs(expected[i + element] - actual[i + element]) < 1.0e-6f,
                        "corner " + (i / ObjMesh.STRIDE) + " element " + element + ": text "
                                + expected[i + element] + " vs binary " + actual[i + element]);
            }
            helper.assertTrue(actual[i + 8] == 1.0f,
                    "the fixture's material id must survive as the exact float 1.0, got " + actual[i + 8]);
        }

        helper.succeed();
    }

    /**
     * Packed white is the float bit pattern 0xFFFFFFFF -- a NaN. Float arithmetic or a narrowing
     * cast turns it into garbage (or 0), and the wheel then draws black with no error anywhere.
     * The material id lives in data4.x instead, at full precision, so 0.5 must stay exactly 0.5.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void materialIdSurvivesAsExactFloat(GameTestHelper helper) {
        byte[] binary = oneTriangleBin(0xFFFFFFFF, 0.5f);
        ObjMesh mesh;
        try {
            mesh = ObjMesh.parseBinary(binary);
        } catch (IOException e) {
            helper.assertTrue(false, "a valid one-triangle FRRB must parse: " + e.getMessage());
            return;
        }
        helper.assertTrue(mesh != null && mesh.corners().length >= ObjMesh.STRIDE,
                "the one-triangle FRRB must produce at least one corner");
        if (mesh == null || mesh.corners().length < ObjMesh.STRIDE) {
            return;
        }

        float[] corner = mesh.corners();
        helper.assertTrue(corner[8] == 0.5f,
                "data4.x = 0.5f must decode to exactly 0.5, got " + corner[8]
                        + " -- quantising it through the packed colour would break the shader's material bucket");
        helper.assertTrue(corner[9] == 1.0f && corner[10] == 1.0f,
                "packed white RGBA (0xFFFFFFFF, a NaN float) must decode to green/blue 1.0/1.0 via "
                        + "Float.floatToRawIntBits, got " + corner[9] + "/" + corner[10]);
        for (float value : corner) {
            helper.assertTrue(Float.isFinite(value),
                    "no NaN from the packed colour may leak into a corner, got " + value);
        }

        // A genuinely white corner (dharma/soe have no colour attribute): material id 1.0 plus the
        // NaN packed bits must give 1.0/1.0/1.0, not a dark or garbled tint.
        try {
            float[] white = ObjMesh.parseBinary(oneTriangleBin(0xFFFFFFFF, 1.0f)).corners();
            helper.assertTrue(white[8] == 1.0f && white[9] == 1.0f && white[10] == 1.0f,
                    "packed white must decode to 1.0/1.0/1.0, got " + white[8] + "/" + white[9] + "/" + white[10]);
        } catch (IOException e) {
            helper.assertTrue(false, "a white one-triangle FRRB must parse: " + e.getMessage());
        }

        helper.succeed();
    }

    /** A corrupt binary must be rejected on the named header field before any mesh is allocated. */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void corruptHeadersAreRejectedBeforeAllocation(GameTestHelper helper) {
        byte[] valid = oneTriangleBin(0xFFFFFFFF, 1.0f);

        byte[] badMagic = valid.clone();
        badMagic[0] = 'X';
        expectBad(helper, badMagic, "magic");

        byte[] badVersion = valid.clone();
        ByteBuffer.wrap(badVersion).order(ByteOrder.LITTLE_ENDIAN).putInt(4, 2);
        expectBad(helper, badVersion, "version");

        byte[] hugeCorners = valid.clone();
        ByteBuffer.wrap(hugeCorners).order(ByteOrder.LITTLE_ENDIAN).putInt(12, -1); // 0xFFFFFFFF
        expectBad(helper, hugeCorners, "cornerCount");

        expectBad(helper, Arrays.copyOf(valid, valid.length - 10), "byteCount");

        helper.succeed();
    }

    private static void expectBad(GameTestHelper helper, byte[] bytes, String field) {
        try {
            ObjMesh.parseBinary(bytes);
            helper.assertTrue(false, "a corrupt FRRB with a bad " + field + " must throw IOException");
        } catch (IOException e) {
            helper.assertTrue(e.getMessage() != null && e.getMessage().contains(field),
                    "the rejection of a bad " + field + " must name that field, got: " + e.getMessage());
        }
    }

    /** A hand-built, valid one-triangle FRRB v1 file: 3 corners, 3 indices, one face. */
    private static byte[] oneTriangleBin(int packedRgbaBits, float materialId) {
        int corners = 3;
        int total = 32 + corners * 56 + corners * 4 + 8 + 28;
        ByteBuffer buffer = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("FRRB".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(1).putInt(6).putInt(corners).putInt(corners).putInt(0).putInt(0).putInt(total);

        float[][] positions = {{0.0f, 0.0f, 0.0f}, {1.0f, 0.0f, 0.0f}, {0.0f, 1.0f, 0.0f}};
        float[][] uvs = {{0.0f, 0.0f}, {1.0f, 0.0f}, {0.0f, 1.0f}};
        for (int c = 0; c < corners; c++) {
            buffer.putFloat(positions[c][0]).putFloat(positions[c][1]).putFloat(positions[c][2]);
            buffer.putInt(packedRgbaBits); // packed colour: raw float bits, NOT float arithmetic
            buffer.putFloat(materialId).putFloat(0.0f).putFloat(0.0f).putFloat(0.0f); // data4
            buffer.putFloat(0.0f).putFloat(0.0f).putFloat(1.0f).putFloat(0.0f); // aux4: normal
            buffer.putFloat(uvs[c][0]).putFloat(uvs[c][1]);
        }
        for (int c = 0; c < corners; c++) {
            buffer.putInt(c); // layer 1: sequential indices
        }
        buffer.putInt(0).putInt(0); // layer 4: face 0 -> group 0 / material 0
        buffer.putInt(1); // layer 5: faceCount
        buffer.putFloat(0.0f).putFloat(0.0f).putFloat(0.0f); // centre (positions already centred)
        buffer.putFloat(1.0f).putFloat(1.0f); // radius, height
        buffer.putInt(0); // reserved
        return buffer.array();
    }

    private static byte[] bytes(String path) {
        try (InputStream in = open(path)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    /** Test resources are mounted on the gametest run classpath, but the mod's own class loader
     *  only sees main resources -- so try the JVM class loaders as well. */
    private static InputStream open(String path) {
        InputStream in = ObjMeshTests.class.getResourceAsStream(path);
        if (in != null) {
            return in;
        }
        String name = path.startsWith("/") ? path.substring(1) : path;
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        in = context != null ? context.getResourceAsStream(name) : null;
        return in != null ? in : ClassLoader.getSystemResourceAsStream(name);
    }

    private static ObjMesh shipped() {
        return load(WHEEL_OBJ);
    }

    private static ObjMesh load(String path) {
        byte[] bytes = bytes(path);
        if (bytes == null) {
            return null;
        }
        try {
            return ObjMesh.parseBinary(bytes);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** The fixture OBJ stays text: it is the reference the binary is compared against. */
    private static ObjMesh loadText(String path) {
        try (InputStream in = open(path)) {
            if (in == null) {
                return null;
            }
            return ObjMesh.parse(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static ObjMesh parse(String obj) {
        try {
            return ObjMesh.parse(new BufferedReader(new StringReader(obj)));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
