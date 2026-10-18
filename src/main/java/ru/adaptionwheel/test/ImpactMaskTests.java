package ru.adaptionwheel.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.AdaptionWheel;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Pins that the optional impact-frame masks ship, are real PNGs, and carry the size the client
 * resolver reads out of the header.
 *
 * <p>Nothing else would notice their absence. A missing {@code frame_N.png} is a perfectly normal
 * case for the panel — {@code ImpactMaskResolver} returns {@code null} and the procedural two-tone
 * stands in — so a build that forgot to package them looks exactly like a build that chose not to
 * use masks at all. And a mask whose header cannot be read silently falls back to an assumed 640x640
 * crop size, which merely draws it slightly wrong rather than failing.
 *
 * <p>Deliberately does not touch {@code ImpactMaskResolver} itself: its {@code AbstractTexture} is a
 * client-only class and the gametest server has no such thing, so this asserts against the same
 * resource-manager lookup and the same header offsets instead.
 */
@GameTestHolder(AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class ImpactMaskTests {

    /** The frame count the flash ships with, and therefore how many masks must be present. */
    private static final int FRAME_COUNT = 5;

    /** The size the shipped masks are authored at, which the crop maths depends on. */
    private static final int EXPECTED_SIZE = 640;

    private static ResourceLocation maskId(int frame) {
        return ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID,
                "textures/effect/frame_" + frame + ".png");
    }

    private static int[] pngSize(Resource data) throws IOException {
        try (InputStream in = data.open()) {
            byte[] header = in.readNBytes(24);
            if (header.length < 24 || header[0] != (byte) 0x89 || header[1] != 'P'
                    || header[2] != 'N' || header[3] != 'G') {
                return new int[]{-1, -1};
            }
            return new int[]{readInt(header, 16), readInt(header, 20)};
        }
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    /** Every mask the flash step can ask for has to be on the classpath, or the feature is absent. */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void everyShippedMaskFrameExists(GameTestHelper helper) {
        var resources = helper.getLevel().getServer().getResourceManager();
        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            ResourceLocation id = maskId(frame);
            Optional<Resource> resource = resources.getResource(id);
            helper.assertTrue(resource.isPresent(),
                    "impact mask " + frame + " must ship at " + id
                            + ", or the panel silently falls back to the procedural two-tone");
        }
        helper.succeed();
    }

    /** A mask with the wrong size crops wrong (it is not a load failure, so nothing reports it). */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void everyShippedMaskIsAPngOfTheExpectedSize(GameTestHelper helper) throws IOException {
        var resources = helper.getLevel().getServer().getResourceManager();
        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            ResourceLocation id = maskId(frame);
            Optional<Resource> resource = resources.getResource(id);
            if (resource.isEmpty()) {
                continue;
            }
            int[] size = pngSize(resource.get());
            helper.assertTrue(size[0] == EXPECTED_SIZE && size[1] == EXPECTED_SIZE,
                    "impact mask " + frame + " must be a " + EXPECTED_SIZE + "x" + EXPECTED_SIZE
                            + " PNG, found " + size[0] + "x" + size[1]
                            + " (a non-PNG lands on -1x-1, an unreadable header on the crop fallback)");
        }
        helper.succeed();
    }
}
