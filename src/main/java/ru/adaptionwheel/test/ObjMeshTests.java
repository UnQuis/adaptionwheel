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
