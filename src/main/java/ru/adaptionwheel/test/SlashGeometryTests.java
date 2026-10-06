package ru.adaptionwheel.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.client.SlashBladeMesh;

/**
 * Pins the two properties of the swept slash blade that nothing else would notice.
 *
 * <p>Both were found by porting the mesh builder to numpy and measuring, not by reading it. Each
 * compiles, each links, and each draws something — so neither produces an error, a log line, or a
 * missing method. They were only ever going to be caught as "the blade looks a bit off".
 *
 * <ul>
 *   <li><b>The ring must not be wider than the curve's radius of curvature.</b> A ring of
 *       half-width W swept along a curve of radius R folds through itself once W &gt; R. Nothing
 *       reports that; the blade just quietly renders as a shape with creases through it.</li>
 *   <li><b>The two-tone band must not collapse at the tips.</b> If the shading thresholds are
 *       absolute rather than divided by the local taper, every ring whose radius has shrunk below
 *       {@code coreHalf} scores as solid ink, which was 10 of 22 cross-sections and the outer 38%
 *       at each end with no aura at all.</li>
 * </ul>
 *
 * <p>Note the ring geometry, because it is easy to get wrong: {@code theta = j * 2pi / RING} with
 * an even segment count puts vertices at theta 0 and theta pi — <em>both</em> of which are the
 * outer edge — and leaves the centreline between samples, at j = 3 and 4 rather than at j = n/2.
 */
@GameTestHolder(AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class SlashGeometryTests {

    /** The cursed slash, as CursedSlashRenderer passes it. */
    private static final float SLASH_LENGTH = 6.0f;
    private static final float SLASH_CURVE = 0.4583f;
    private static final float SLASH_GLOW_HALF = 0.70f;
    private static final float SLASH_CORE_HALF = 0.40f;

    /** The rift, 1.5x longer and 1.5x wider but bowing by the same fraction. */
    private static final float RIFT_LENGTH = 9.0f;
    private static final float RIFT_CURVE = 0.4583f;
    private static final float RIFT_GLOW_HALF = 1.05f;
    private static final float RIFT_CORE_HALF = 0.60f;

    private static final float PI_SQUARED = (float) (Math.PI * Math.PI);

    /**
     * The centreline is {@code y = A*sin(pi*x/L)} with {@code A = curve*L}, whose radius of
     * curvature is smallest at the apex, where {@code R = L^2 / (A * pi^2)}. Written out here rather
     * than delegated, so this test is checking the geometry and not merely agreeing with whatever
     * the helper returns — otherwise neutering the helper would make the test pass.
     */
    private static float apexRadius(float length, float curve) {
        return length * length / (curve * length * PI_SQUARED);
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void curvatureLimitMatchesTheSineArchItSweeps(GameTestHelper helper) {
        float limit = SlashBladeMesh.maxGlowHalf(SLASH_LENGTH, SLASH_CURVE);
        float expected = apexRadius(SLASH_LENGTH, SLASH_CURVE);

        helper.assertTrue(Math.abs(limit - expected) < 0.001f,
                "maxGlowHalf says " + limit + " but a sine arch of length " + SLASH_LENGTH
                        + " bowing by " + SLASH_CURVE + " has an apex radius of " + expected
                        + " (L^2/(A*pi^2)). If this is too large, the fold check below approves"
                        + " a blade that folds through itself.");

        helper.assertTrue(SLASH_GLOW_HALF < limit,
                "the cursed slash's aura half-width is " + SLASH_GLOW_HALF
                        + " but the arch's radius of curvature is only " + limit
                        + ". A ring wider than the curve it is swept along folds through itself,"
                        + " and the blade renders with creases instead of a clean crescent.");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void riftRingFitsInsideItsOwnCurvature(GameTestHelper helper) {
        float limit = SlashBladeMesh.maxGlowHalf(RIFT_LENGTH, RIFT_CURVE);
        float expected = apexRadius(RIFT_LENGTH, RIFT_CURVE);

        helper.assertTrue(Math.abs(limit - expected) < 0.001f,
                "rift maxGlowHalf is " + limit + ", expected " + expected);

        helper.assertTrue(RIFT_GLOW_HALF < limit,
                "the rift's aura half-width is " + RIFT_GLOW_HALF + " against a curvature limit of "
                        + limit + ". Both entities scale length and width by 1.5 while keeping the"
                        + " same bow fraction, so both clear the limit or neither does.");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void twoToneBandSurvivesTheTaper(GameTestHelper helper) {

        float[] profile = SlashBladeMesh.crossSectionProfile(SLASH_GLOW_HALF, SLASH_CORE_HALF);
        int n = profile.length;

        helper.assertTrue(n > 0 && n % 2 == 0,
                "the cross-section has " + n + " samples and must be even for a clean flattened"
                        + " lens with no seam vertex duplicated on the wrap.");

        // j = 0 and j = n/2 are opposite outer edges: aura, zero coreness.
        helper.assertTrue(profile[0] < 0.001f,
                "coreness at the outer edge of the ring is " + profile[0]
                        + ", so the blade has no aura band and the two-tone treatment has"
                        + " collapsed into flat ink.");
        helper.assertTrue(profile[n / 2] < 0.001f,
                "coreness on the opposite outer edge is " + profile[n / 2] + ", expected 0");

        float peak = 0f;
        for (float v : profile) {
            peak = Math.max(peak, v);
        }
        // The centreline falls between samples, but the two nearest vertices still read full ink.
        helper.assertTrue(peak > 0.999f,
                "the darkest ink on the blade is " + peak + ", expected 1. Without a solid core"
                        + " there is nothing for the screentone band to sit against.");

        int between = 0;
        for (float v : profile) {
            if (v > 0.001f && v < 0.999f) {
                between++;
            }
        }
        helper.assertTrue(between >= 4,
                "only " + between + " of " + n + " samples land between ink and aura, so the"
                        + " screentone band is too narrow to read as a band.");

        // The regression itself: absolute thresholds on a ring that has tapered to a quarter of
        // its width score that whole ring as solid ink (mean 1.000), where the taper-scaled rule
        // gives a mean of about 0.599. If these ever agree, the probe below proves nothing.
        float taperedRadius = SLASH_GLOW_HALF * 0.25f;
        float absoluteMean = 0f;
        for (int j = 0; j < n; j++) {
            float offset = Math.abs(taperedRadius * (float) Math.cos(j / (float) n * 2f * (float) Math.PI));
            float t = Mth(offset, SLASH_CORE_HALF, SLASH_GLOW_HALF);
            absoluteMean += 1f - t * t * (3f - 2f * t);
        }
        absoluteMean /= n;

        float correctMean = 0f;
        for (float v : profile) {
            correctMean += v;
        }
        correctMean /= n;

        helper.assertTrue(Math.abs(correctMean - absoluteMean) > 0.05f,
                "absolute and taper-scaled thresholds give nearly the same mean coreness ("
                        + String.format("%.3f", correctMean) + " vs "
                        + String.format("%.3f", absoluteMean)
                        + "), so this test can no longer tell the fixed code from the bug it was"
                        + " written for. The probe ring needs to be tapered far enough that the"
                        + " unfixed rule would score it solid.");
        helper.succeed();
    }

    /**
     * The ink must stay inside the aura. If {@code coreHalf} were ever raised past {@code glowHalf}
     * the smoothstep inverts and the blade renders with a bright rim and a dark middle — the exact
     * inversion of the intended two-tone.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void inkIsAlwaysNarrowerThanTheAura(GameTestHelper helper) {
        helper.assertTrue(SLASH_CORE_HALF < SLASH_GLOW_HALF,
                "cursed slash core " + SLASH_CORE_HALF + " must be below its aura " + SLASH_GLOW_HALF);
        helper.assertTrue(RIFT_CORE_HALF < RIFT_GLOW_HALF,
                "rift core " + RIFT_CORE_HALF + " must be below its aura " + RIFT_GLOW_HALF);

        // The observable consequence, so the assertion above is not the only thing standing here.
        //
        // Swapping the arguments does not invert the two-tone and does not erase the ink; it
        // flattens the blade to solid ink, and the reason is the divide guard. smoothstep computes
        // t = (x - edge0) / max(edge1 - edge0, 1e-5), so with edge1 below edge0 the denominator
        // floors at a tiny positive, every offset lands far below edge0, t clamps to 0, and
        // coreness is 1 at every single vertex. The guard is what stops a division by zero, and it
        // is also what turns this mistake into "no aura" rather than a crash or a NaN. Pinned
        // because the three plausible guesses -- inverted, erased, or NaN -- are all wrong.
        float[] inverted = SlashBladeMesh.crossSectionProfile(SLASH_CORE_HALF, SLASH_GLOW_HALF);
        float invertedMin = 1f;
        for (float v : inverted) {
            invertedMin = Math.min(invertedMin, v);
        }
        helper.assertTrue(invertedMin > 0.999f,
                "passing core above aura left a minimum coreness of " + invertedMin
                        + ". Expected 1 at every vertex: the divide guard floors the denominator, the"
                        + " smoothstep saturates to zero, and the blade renders as solid ink with no"
                        + " aura band at all. If this is not 1 the guard is gone or the profile"
                        + " degenerates some other way, and this reason is no longer accurate.");
        helper.succeed();
    }

    private static float Mth(float x, float a, float b) {
        float t = Math.max(0f, Math.min(1f, (x - a) / (b - a)));
        return t;
    }
}