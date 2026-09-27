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

import java.util.Set;

/**
 * The command tree must be serializable to the client, or nobody can log in.
 *
 * <p>Registering a custom {@link ArgumentType} parses correctly and then breaks the game at
 * login. The command tree is mirrored to the client, and the client rebuilds each node from a
 * serializer looked up by the argument type's class in {@code ArgumentTypeInfos}' private static
 * map, which is filled from a hardcoded bootstrap list. There is no NeoForge registration hook.
 * So the server happily builds the tree, {@code ClientboundCommandsPacket} throws
 * {@code Unrecognized argument type}, and the client drops the connection with
 * {@code Invalid player data} — a total failure to join, not a broken command.</p>
 *
 * <p>That is exactly what happened once already, and no unit test or dedicated-server boot
 * caught it, because both are server-side. These tests walk the real tree this mod registers and
 * assert every argument type in it is one the client can deserialize.</p>
 */
@GameTestHolder(ru.adaptionwheel.AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public final class CommandTreeSyncTests {

    private CommandTreeSyncTests() {
    }

    /** The dispatcher this mod's command handler builds into, in isolation. */
    private static CommandDispatcher<CommandSourceStack> modDispatcher() {
        CommandBuildContext context = Commands.createValidationContext(VanillaRegistries.createLookup());
        CommandDispatcher<CommandSourceStack> dispatcher =
                new Commands(Commands.CommandSelection.ALL, context).getDispatcher();
        AdaptionCommand.onRegisterCommands(
                new RegisterCommandsEvent(dispatcher, Commands.CommandSelection.ALL, context));
        return dispatcher;
    }

    /**
     * The direct reproduction: every argument type in the tree must be recognized by the
     * serializer registry. This is the same predicate {@code ClientboundCommandsPacket} uses when
     * it throws, so a failure here is a failure to log in.
     */
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

    /** The tree must contain the subcommands the docs promise, not just survive serialization. */
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

    /** {@code grant} must offer the bulk form, and it must be reachable with no extra argument. */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void grantOffersABulkForm(GameTestHelper helper) {
        var grant = modDispatcher().getRoot().getChild("adaptionwheel").getChild("grant");
        helper.assertTrue(grant.getChild("all") != null, "/adaptionwheel grant all must exist");
        helper.assertTrue(grant.getChild("concept") != null,
                "/adaptionwheel grant <concept> must exist");
        helper.succeed();
    }

    /**
     * A custom argument type is the mistake this file exists to catch, so assert the constraint
     * itself rather than only the absence of one: a locally declared type is rejected by the
     * registry, which is why the concept argument has to be a vanilla one.
     */
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

    /**
     * Grants route through the registry, and the bulk form iterates it — so an unregistered or
     * unencodable concept key would make {@code grant all} produce garbage. Keep the registry
     * enumerable and non-empty so that path is meaningful.
     */
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
