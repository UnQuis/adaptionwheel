package ru.adaptionwheel.test;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.AdaptionWheel;

/**
 * Pins that the Adaptation Temple's structure template actually contains blocks on this branch.
 *
 * <p>Every failure mode here is silent, which is why the temple could ship as a structure that
 * placed successfully and showed nothing at all:
 *
 * <ul>
 *   <li>The palette key is branch specific. {@code NbtUtils.readBlockState} reads {@code "Name"} on
 *       1.21.1 and {@code "id"} on 26.3, and returns {@code Blocks.AIR} for anything else — no
 *       exception, no log line. A template written in the other branch's shape is 100% air.</li>
 *   <li>A block id that does not exist here is also air, by the same branch. 1.21.1 has one
 *       lightning rod; the copper family around it arrived later, so a template naming
 *       {@code oxidized_lightning_rod} silently loses that block.</li>
 * </ul>
 *
 * <p>So the structure itself was never broken — the placement, the biome tag, the spacing and the
 * template pool were all fine. The temple was there, made of nothing.
 */
@GameTestHolder(AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class TempleStructureTests {

    private static final ResourceLocation TEMPLATE =
            ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "adaptation_temple");

    private static StructureTemplate load(GameTestHelper helper) {
        return helper.getLevel().getServer().getStructureManager().get(TEMPLATE)
                .orElseThrow(() -> new AssertionError(
                        "temple template did not load at all: " + TEMPLATE));
    }

    private static int count(StructureTemplate template, Block block) {
        List<StructureTemplate.StructureBlockInfo> found =
                template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), block, false);
        return found.size();
    }

    /** The palette must be in the spelling this branch's loader reads, or every block is air. */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void templeIsNotMadeOfAir(GameTestHelper helper) {

        StructureTemplate template = load(helper);
        int air = count(template, Blocks.AIR);

        helper.assertTrue(air == 0, "the temple template resolved to " + air
                + " air blocks. Its palette keys do not match what this branch reads: 1.21.1 wants "
                + "\"Name\"/\"Properties\", 26.3 wants \"id\"/\"properties\". NbtUtils.readBlockState "
                + "returns air for the other spelling without saying anything.");
        helper.assertTrue(count(template, Blocks.STONE_BRICKS) > 0,
                "the temple template has no stone bricks in it, so it is not the temple");
        helper.assertTrue(template.getSize().getX() > 1 && template.getSize().getY() > 1,
                "the temple template is flat: " + template.getSize());
        helper.succeed();
    }

    /**
     * Every block in the template must resolve on this branch. The lightning rod is the one that
     * did not: 1.21.1 ships exactly one, the copper family arrived in a later update.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void everyTempleBlockExistsOnThisBranch(GameTestHelper helper) {

        StructureTemplate template = load(helper);

        helper.assertTrue(count(template, BuiltInRegistries.BLOCK.get(
                        ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "resonance_altar"))) > 0,
                "the temple's altar is missing, so the mod's own block id did not resolve");
        helper.assertTrue(count(template, Blocks.LIGHTNING_ROD) > 0,
                "the temple's lightning rod is missing. It must be named for this branch: 1.21.1 has "
                        + "minecraft:lightning_rod and no copper family around it.");
        helper.succeed();
    }
}
