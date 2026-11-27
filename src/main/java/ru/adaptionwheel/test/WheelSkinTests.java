package ru.adaptionwheel.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.data.ModDataComponents;
import ru.adaptionwheel.data.WheelSkins;
import ru.adaptionwheel.item.ModItems;

/**
 * Pins the item-stored skin choice. The component has to survive a save/load and an anvil upgrade on
 * its own: it is deliberately NOT part of {@code WheelData}, which {@code saveToStack} rebuilds and
 * would erase it. A skin that silently reverts to dharma is the failure this class exists to catch.
 */
@GameTestHolder(AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class WheelSkinTests {

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theSkinComponentRoundTrips(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.MAHORAGA_WHEEL.get());

        String current = WheelSkins.normalize(stack.get(ModDataComponents.WHEEL_SKIN.get()));
        helper.assertTrue(WheelSkins.DHARMA.equals(current),
                "an absent WHEEL_SKIN component must read back as dharma, got " + current);

        stack.set(ModDataComponents.WHEEL_SKIN.get(), WheelSkins.ORBITAL);
        String readBack = WheelSkins.normalize(stack.get(ModDataComponents.WHEEL_SKIN.get()));
        helper.assertTrue(WheelSkins.ORBITAL.equals(readBack),
                "a stored WHEEL_SKIN component must round trip as orbital, got " + readBack);

        helper.assertTrue(!WheelSkins.isValid("nope"),
                "an unknown skin id must be rejected by the whitelist");
        helper.assertTrue(WheelSkins.isValid("orbital"),
                "the orbital id must be accepted by the whitelist");

        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theSkinWhitelistRejectsUnknownIds(GameTestHelper helper) {
        helper.assertTrue(WheelSkins.isValid("dharma"),
                "the dharma id must be accepted by the whitelist");
        helper.assertTrue(WheelSkins.isValid("orbital"),
                "the orbital id must be accepted by the whitelist");
        helper.assertTrue(!WheelSkins.isValid("orbital:else"),
                "a namespaced/unknown skin id must be rejected by the whitelist");
        helper.assertTrue(!WheelSkins.isValid(""),
                "an empty skin id must be rejected by the whitelist");

        helper.succeed();
    }

    /**
     * The CPU-lit fallback cannot feed the orbital mesh's vertex colours straight into
     * {@code setColor}: red carries a material id, so the three shells would draw black,
     * dark grey and red. These are the explicit tints it must decode that id into instead.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void materialIdsDecodeToFallbackTints(GameTestHelper helper) {
        assertTint(helper, WheelSkins.tintForMaterial(0.0f), 0.35f, 0.35f, 0.35f, "dark metal");
        assertTint(helper, WheelSkins.tintForMaterial(0.5f), 0.05f, 0.80f, 1.00f, "emissive shell");
        assertTint(helper, WheelSkins.tintForMaterial(1.0f), 1.00f, 1.00f, 1.00f, "fabric");

        helper.succeed();
    }

    /**
     * The decoded orbital tint is used as-is: the shader ignores WheelTint on that branch, so the
     * CPU fallback must not multiply the authored shell colour by the adaptation tint. Dharma keeps
     * the old multiply.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void orbitalFallbackDoesNotApplyTheAdaptationTint(GameTestHelper helper) {
        float adaptation = 0.29f; // a partly-adapted wheel's grey
        helper.assertTrue(WheelSkins.fallbackTint(true, 0.05f, adaptation) == 0.05f,
                "orbital must keep the decoded material tint, not multiply by the adaptation tint");
        helper.assertTrue(WheelSkins.fallbackTint(true, 1.00f, adaptation) == 1.00f,
                "an orbital fabric shell must stay white regardless of the adaptation tint");
        helper.assertTrue(Math.abs(WheelSkins.fallbackTint(false, 1.0f, adaptation) - adaptation) < 0.001f,
                "dharma must still multiply the material by the adaptation tint");

        helper.succeed();
    }

    private static void assertTint(GameTestHelper helper, float[] actual,
                                   float r, float g, float b, String material) {
        helper.assertTrue(actual != null && actual.length == 3,
                "tintForMaterial must return three channels for " + material);
        if (actual == null || actual.length != 3) {
            return;
        }
        helper.assertTrue(Math.abs(actual[0] - r) < 0.001f
                        && Math.abs(actual[1] - g) < 0.001f
                        && Math.abs(actual[2] - b) < 0.001f,
                "the fallback tint for " + material + " must be (" + r + ", " + g + ", " + b
                        + "), got (" + actual[0] + ", " + actual[1] + ", " + actual[2] + ")");
    }
}
