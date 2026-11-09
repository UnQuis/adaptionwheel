package ru.adaptionwheel.test;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
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
        RootCommandNode<CommandSourceStack> root = modDispatcher().getRoot();
        CommandNode<CommandSourceStack> mod = root.getChild("adaptionwheel");
        helper.assertTrue(mod != null, "/adaptionwheel must exist");
        for (String sub : new String[]{"status", "list", "info", "grant", "ungrant", "analyze",
                "reset", "registry"}) {
            helper.assertTrue(mod.getChild(sub) != null, "/adaptionwheel " + sub + " must exist");
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void grantOffersABulkForm(GameTestHelper helper) {
        CommandNode<CommandSourceStack> grant =
                modDispatcher().getRoot().getChild("adaptionwheel").getChild("grant");
        helper.assertTrue(grant.getChild("all") != null, "/adaptionwheel grant all must exist");
        helper.assertTrue(grant.getChild("concept") != null,
                "/adaptionwheel grant <concept> must exist");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void grantAcceptsATargetWithoutALevel(GameTestHelper helper) {
        CommandNode<CommandSourceStack> concept = modDispatcher().getRoot().getChild("adaptionwheel")
                .getChild("grant").getChild("concept");
        helper.assertTrue(concept != null, "the concept node must exist");
        List<String> kids = childNames(concept);
        helper.assertTrue(kids.contains("target"),
                "grant <concept> <player> must exist; nesting it under <level> leaves "
                        + "'Expected integer' pointing at the player's name. Children: " + kids);
        helper.assertTrue(kids.contains("level"), "grant <concept> level must exist: " + kids);
        CommandNode<CommandSourceStack> level = concept.getChild("level");
        helper.assertTrue(level != null && childNames(level).contains("level"),
                "grant <concept> level <level> must exist: "
                        + (level == null ? "no level node" : childNames(level)));
        CommandNode<CommandSourceStack> amount = level == null ? null : level.getChild("level");
        helper.assertTrue(amount != null && childNames(amount).contains("target"),
                "grant <concept> level <level> <player> must exist: "
                        + (amount == null ? "no amount node" : childNames(amount)));
        helper.succeed();
    }

    private static List<String> childNames(CommandNode<?> node) {
        return node.getChildren().stream().map(CommandNode::getName)
                .sorted().toList();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void ungrantAcceptsAConceptAndTarget(GameTestHelper helper) {
        CommandNode<CommandSourceStack> ungrant =
                modDispatcher().getRoot().getChild("adaptionwheel").getChild("ungrant");
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
            public String parse(StringReader reader) {
                return reader.readUnquotedString();
            }
        };
        helper.assertTrue(!ArgumentTypeInfos.isClassRecognized(homemade.getClass()),
                "an unregistered argument type must not be recognized; if Brigadier ever grows a "
                        + "registration hook this test can be revisited");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void grantExposesStructuredSelectors(GameTestHelper helper) {
        CommandNode<CommandSourceStack> grant =
                modDispatcher().getRoot().getChild("adaptionwheel").getChild("grant");
        for (String sub : new String[]{"all", "concept", "type", "env", "contact", "offense",
                "drop", "existence", "debuff"}) {
            helper.assertTrue(grant.getChild(sub) != null, "grant " + sub + " must exist: structured "
                    + "selectors are how a player grants a mob adaptation without quoting a colon");
        }
        helper.assertTrue(grant.getChild("contact").getChild("mob") != null,
                "grant contact <mob> must exist");
        helper.assertTrue(grant.getChild("type").getChild("mob") != null,
                "grant type mob (the Mob Attacks ladder) must be a literal choice");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theCommandHasAShortAlias(GameTestHelper helper) {
        CommandNode<CommandSourceStack> alias = modDispatcher().getRoot().getChild("aw");
        helper.assertTrue(alias != null, "/aw must exist as an alias of /adaptionwheel");
        for (String sub : new String[]{"status", "list", "info", "grant", "ungrant", "analyze",
                "reset", "registry"}) {
            helper.assertTrue(alias.getChild(sub) != null,
                    "/aw " + sub + " must exist: the alias is the full tree, not a stub");
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void suggestionsCoverDynamicFamilies(GameTestHelper helper) {
        List<String> zomb = AdaptionCommand.conceptSuggestionIds("zomb");
        helper.assertTrue(zomb.contains("Contact_minecraft:zombie"),
                "typing 'zomb' must suggest Contact_minecraft:zombie, got " + zomb);
        helper.assertTrue(zomb.contains("Offense_NPC_minecraft:zombie"),
                "the offense family must be suggested the same way, got " + zomb);
        List<String> mobs = AdaptionCommand.entitySuggestionIds("zomb");
        helper.assertTrue(mobs.contains("minecraft:zombie"),
                "the <mob> argument must suggest minecraft:zombie, got " + mobs);
        List<String> empty = AdaptionCommand.conceptSuggestionIds("");
        helper.assertTrue(empty.contains("Type_MOB"),
                "an empty input must list the registered Type_ ladders, got " + empty.size() + " entries");
        helper.assertTrue(AdaptionCommand.conceptSuggestionIds("fire").contains("Env_Fire"),
                "typing 'fire' must surface Env_Fire");
        helper.assertTrue(AdaptionCommand.effectSuggestionIds("poison").contains("minecraft:poison"),
                "the <effect> argument must suggest minecraft:poison");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theRegistryIsEnumerableForBulkGrants(GameTestHelper helper) {
        List<AdaptationDefinition> all = AdaptationRegistry.allDefinitions();
        helper.assertTrue(all.size() > 20, "expected the full concept registry, got " + all.size());
        for (AdaptationDefinition def : all) {
            helper.assertTrue(def.concept() != null && !def.concept().isEmpty(),
                    "every definition must carry a usable key");
        }
        helper.succeed();
    }

    private static List<Suggestion> completions(GameTestHelper helper, String typed) {
        CommandDispatcher<CommandSourceStack> dispatcher = modDispatcher();
        CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack();
        Suggestions suggestions = dispatcher
                .getCompletionSuggestions(dispatcher.parse(typed, source))
                .join();
        return suggestions.getList();
    }

    private static void applyingTheSuggestionMustParse(GameTestHelper helper, String typed, Suggestion pick) {
        String line = typed.substring(0, pick.getRange().getStart()) + pick.getText();
        try {
            ParseResults<CommandSourceStack> reParsed =
                    modDispatcher().parse(line, helper.getLevel().getServer().createCommandSourceStack());
            helper.assertTrue(!reParsed.getReader().canRead(),
                    "after applying the suggestion the line must parse fully; line=" + line
                            + " rest=" + reParsed.getReader().getRemaining());
        } catch (Exception e) {
            helper.assertTrue(false, "the applied suggestion must parse; line=" + line
                    + " error=" + e.getMessage());
        }
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aColonSuggestionRoundTripsIntoAParsableLine(GameTestHelper helper) {
        String typed = "aw grant Contact_minecraft";
        List<Suggestion> suggestions = completions(helper, typed);
        helper.assertFalse(suggestions.isEmpty(), "typing a partial family id must offer suggestions");
        List<String> texts = suggestions.stream().map(Suggestion::getText).toList();
        Suggestion pick = suggestions.stream().filter(s -> s.getText().contains(":")).findFirst().orElse(null);
        helper.assertTrue(pick != null, "a colon-bearing dynamic id must be offered, got: " + texts);
        if (pick != null) {
            helper.assertTrue(pick.getText().startsWith("\"") && pick.getText().endsWith("\""),
                    "a colon-bearing suggestion must be quoted, got: " + texts);
            applyingTheSuggestionMustParse(helper, typed, pick);
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theMobSelectorRoundTripsUnquoted(GameTestHelper helper) {
        String typed = "aw grant contact minecraft:z";
        List<Suggestion> suggestions = completions(helper, typed);
        List<String> texts = suggestions.stream().map(Suggestion::getText).toList();
        Suggestion pick = suggestions.stream()
                .filter(s -> s.getText().equals("minecraft:zombie")).findFirst().orElse(null);
        helper.assertTrue(pick != null,
                "the <mob> argument must suggest unquoted registry ids, got: " + texts);
        if (pick != null) {
            applyingTheSuggestionMustParse(helper, typed, pick);
        }
        helper.succeed();
    }
}
