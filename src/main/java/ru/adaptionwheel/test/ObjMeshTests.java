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
import java.nio.charset.StandardCharsets;

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

    private static final String WHEEL_OBJ = "/assets/adaptionwheel/models/entity/dharma_chakra.obj";

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
     * The wheel's two metals are carried as vertex colours, because a Wavefront OBJ has nowhere
     * else to put a material colour: it names an MTL file, and nothing in the game reads one.
     *
     * <p>The failure this pins is silent in both directions. An export without colours leaves the
     * wheel a single flat gold — a plausible-looking wheel, just not the one that was modelled. And
     * a parser that reads a {@code v} line's fourth number as red would turn the file's homogeneous
     * coordinate into a colour, which is white everywhere and looks identical to the first case.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theShippedWheelCarriesItsMetalColours(GameTestHelper helper) {
        ObjMesh mesh = shipped();
        helper.assertTrue(mesh != null, "the wheel OBJ must load before its colours can be checked");
        if (mesh == null) {
            return;
        }

        float[] corners = mesh.corners();
        int distinct = 0;
        float firstR = corners[8], firstG = corners[9], firstB = corners[10];
        for (int i = 0; i < corners.length; i += ObjMesh.STRIDE) {
            float r = corners[i + 8], g = corners[i + 9], b = corners[i + 10];
            helper.assertTrue(r >= 0.0f && r <= 1.0f && g >= 0.0f && g <= 1.0f && b >= 0.0f && b <= 1.0f,
                    "a vertex colour outside 0..1 cannot be a multiply: " + r + ", " + g + ", " + b);
            if (Math.abs(r - firstR) > 1.0e-3f || Math.abs(g - firstG) > 1.0e-3f || Math.abs(b - firstB) > 1.0e-3f) {
                distinct++;
            }
        }

        helper.assertTrue(distinct > 0,
                "the shipped wheel must carry more than one colour -- the ring is gold and the beams "
                        + "are bronze, and a file with one colour means the export dropped the materials");
        helper.assertTrue(firstR < 0.99f || firstG < 0.99f || firstB < 0.99f,
                "the first vertex is white, which is what a file without colours produces; the metal "
                        + "is not white");

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

    private static ObjMesh shipped() {
        try (InputStream in = ObjMesh.class.getResourceAsStream(WHEEL_OBJ)) {
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
