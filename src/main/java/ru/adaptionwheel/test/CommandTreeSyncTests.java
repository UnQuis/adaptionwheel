package ru.adaptionwheel.test;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.ArgumentUtils;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.adapt.AdaptationDefinition;
import ru.adaptionwheel.adapt.AdaptationRegistry;
import ru.adaptionwheel.server.AdaptionCommand;

import java.util.List;
import java.util.Set;

@GameTestHolder(ru.adaptionwheel.AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public final class CommandTreeSyncTests {

    private CommandTreeSyncTests() {
    }

    private static CommandDispatcher<CommandSourceStack> modDispatcher() {
        CommandBuildContext context = Commands.createValidationContext(VanillaRegistries.createLookup());
        CommandDispatcher<CommandSourceStack> dispatcher =
                new Commands(Commands.CommandSelection.ALL, context).getDispatcher();
        AdaptionCommand.onRegisterCommands(
                new RegisterCommandsEvent(dispatcher, Commands.CommandSelection.ALL, context));
        return dispatcher;
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void everyArgumentTypeCanBeSentToTheClient(GameTestHelper helper) {
        Set<ArgumentType<?>> used = ArgumentUtils.findUsedArgumentTypes(modDispatcher().getRoot());
        helper.assertTrue(!used.isEmpty(), "the mod's command tree must actually contain arguments");
        for (ArgumentType<?> type : used) {
            helper.assertTrue(ArgumentTypeInfos.isClassRecognized(type.getClass()),
                    "argument type " + type.getClass().getName() + " has no client-side serializer; "
                            + "registering it makes player login fail with 'Invalid player data'. "
                            + "Use a vanilla type (StringArgumentType.string() carries a colon when quoted).");
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theTreeExposesEverySubcommand(GameTestHelper helper) {
        var root = modDispatcher().getRoot();
        var mod = root.getChild("adaptionwheel");
        helper.assertTrue(mod != null, "/adaptionwheel must exist");
        for (String sub : new String[]{"status", "list", "info", "grant", "ungrant", "analyze",
                "reset", "registry"}) {
            helper.assertTrue(mod.getChild(sub) != null, "/adaptionwheel " + sub + " must exist");
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void grantOffersABulkForm(GameTestHelper helper) {
        var grant = modDispatcher().getRoot().getChild("adaptionwheel").getChild("grant");
        helper.assertTrue(grant.getChild("all") != null, "/adaptionwheel grant all must exist");
        helper.assertTrue(grant.getChild("concept") != null,
                "/adaptionwheel grant <concept> must exist");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void grantAcceptsATargetWithoutALevel(GameTestHelper helper) {
        var concept = modDispatcher().getRoot().getChild("adaptionwheel")
                .getChild("grant").getChild("concept");
        helper.assertTrue(concept != null, "the concept node must exist");
        List<String> kids = childNames(concept);
        helper.assertTrue(kids.contains("target"),
                "grant <concept> <player> must exist; nesting it under <level> leaves "
                        + "'Expected integer' pointing at the player's name. Children: " + kids);
        helper.assertTrue(kids.contains("level"), "grant <concept> <level> must exist: " + kids);
        var level = concept.getChild("level");
        helper.assertTrue(level != null && childNames(level).contains("target"),
                "grant <concept> <level> <player> must exist");
        helper.succeed();
    }

private static List<String> childNames(com.mojang.brigadier.tree.CommandNode<?> node) {
        return node.getChildren().stream().map(com.mojang.brigadier.tree.CommandNode::getName)
                .sorted().toList();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void ungrantAcceptsAConceptAndTarget(GameTestHelper helper) {
        var ungrant = modDispatcher().getRoot().getChild("adaptionwheel").getChild("ungrant");
        helper.assertTrue(ungrant != null, "/adaptionwheel ungrant must exist");
        helper.assertTrue(ungrant.getChild("concept") != null, "ungrant <concept> must exist");
        helper.assertTrue(ungrant.getChild("concept").getChild("target") != null,
                "ungrant <concept> <player> must exist");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aLocallyDeclaredArgumentTypeWouldBeRejected(GameTestHelper helper) {
        ArgumentType<String> homemade = new ArgumentType<>() {
            @Override
            public String parse(com.mojang.brigadier.StringReader reader) {
                return reader.readUnquotedString();
            }
        };
        helper.assertTrue(!ArgumentTypeInfos.isClassRecognized(homemade.getClass()),
                "an unregistered argument type must not be recognized; if Brigadier ever grows a "
                        + "registration hook this test can be revisited");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theRegistryIsEnumerableForBulkGrants(GameTestHelper helper) {
        var all = AdaptationRegistry.allDefinitions();
        helper.assertTrue(all.size() > 20, "expected the full concept registry, got " + all.size());
        for (AdaptationDefinition def : all) {
            helper.assertTrue(def.concept() != null && !def.concept().isEmpty(),
                    "every definition must carry a usable key");
        }
        helper.succeed();
    }
}
