package ru.adaptionwheel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;
import ru.adaptionwheel.AdaptionWheel;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Resolves the optional {@code frame_N.png} impact-frame masks without making them a hard resource
 * dependency. The original mod composed its impact frames as authored black/white images; when one
 * is present the panel uses it instead of the built-in procedural two-tone, and when none is the
 * procedural panel stands unchanged.
 *
 * <p>Own textures are shipped under {@code adaptionwheel:textures/effect/}, and the original mod's
 * namespace is honoured first so an {@code impact-frames} resource pack can override them. The
 * mask's pixel size is read from the PNG header rather than queried from GL: {@link AbstractTexture}
 * exposes only {@code getId()}, and the size is needed on the CPU side for the crop maths. Resident
 * dimension is cached per texture id so the header is parsed once.
 */
public final class ImpactMaskResolver {

    /** The original mod's namespaces, tried before this mod's own so a resource pack can override. */
    private static final String[] EXTERNAL_NAMESPACES = {"impact-frames", "impact_frames"};

    private static final String TEXTURE_PATH_PREFIX = "textures/effect/frame_";

    /** Size assumed when a mask exists but its header cannot be read. Own masks are 640x640. */
    private static final int FALLBACK_SIZE = 640;

    private ImpactMaskResolver() {
    }

    /**
     * Loads {@code frame_N.png} for the given flash step from the original namespace first, then
     * this mod's. {@code frame < 0} and missing masks are normal cases and return {@code null}, in
     * which case the caller keeps the procedural panel.
     */
    @Nullable
    public static Mask resolve(Minecraft minecraft, int frame) {
        if (frame < 0) {
            return null;
        }

        String path = TEXTURE_PATH_PREFIX + frame + ".png";
        for (String namespace : EXTERNAL_NAMESPACES) {
            Mask mask = load(minecraft, ResourceLocation.fromNamespaceAndPath(namespace, path));
            if (mask != null) {
                return mask;
            }
        }
        return load(minecraft, ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, path));
    }

    @Nullable
    private static Mask load(Minecraft minecraft, ResourceLocation id) {
        Optional<Resource> resource = minecraft.getResourceManager().getResource(id);
        if (resource.isEmpty()) {
            return null;
        }

        AbstractTexture texture = minecraft.getTextureManager().getTexture(id);
        int[] size = readPngSize(resource.get());
        return new Mask(texture, size[0], size[1]);
    }

    /**
     * Reads width/height out of a PNG IHDR chunk. A PNG begins with an 8-byte signature, then a
     * 4-byte length and the 4-byte literal {@code IHDR}, so the big-endian width and height sit at
     * byte offsets 16 and 20. Anything else falls back to {@link #FALLBACK_SIZE}.
     */
    private static int[] readPngSize(Resource resource) {
        try (InputStream in = resource.open()) {
            byte[] header = in.readNBytes(24);
            if (header.length >= 24
                    && header[0] == (byte) 0x89 && header[1] == 'P'
                    && header[2] == 'N' && header[3] == 'G') {
                int width = readInt(header, 16);
                int height = readInt(header, 20);
                if (width > 0 && height > 0) {
                    return new int[]{width, height};
                }
            }
        } catch (IOException ignored) {
            // A mask that cannot be read simply falls back to its assumed size.
        }
        return new int[]{FALLBACK_SIZE, FALLBACK_SIZE};
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    /** One loaded mask: its texture plus its pixel size, for the shader's crop maths. */
    public record Mask(AbstractTexture texture, int width, int height) {
    }
}
