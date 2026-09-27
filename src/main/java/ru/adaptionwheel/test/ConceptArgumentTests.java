package ru.adaptionwheel.test;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.adapt.AdaptationDefinition;
import ru.adaptionwheel.adapt.AdaptationRegistry;
import ru.adaptionwheel.server.ConceptArgument;

import java.util.List;

/**
 * The concept command argument, which is the one thing standing between an operator and being
 * able to grant the adaptation they actually wanted.
 *
 * <p>It was {@code StringArgumentType.word()}, and that accepts only
 * {@code 0-9 A-Z a-z _ - . +}. Every concept key built from a namespaced id carries a colon —
 * {@code Existence_draconicevolution:draconic_guardian}, and the whole
 * {@code Contact_<mob>} / {@code Offense_NPC_<mob>} / {@code Drop_NPC_<mob>} families — so
 * Brigadier rejected them at the parser. No amount of quoting or retrying helped; the command
 * simply could not be typed, and it failed as a bare "expected word" syntax error that pointed
 * nowhere near the cause.</p>
 *
 * <p>These run the real parser over the real registry, so a concept family that grows a character
 * nobody thought about cannot quietly become ungrantable again.</p>
 */
@GameTestHolder(ru.adaptionwheel.AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public final class ConceptArgumentTests {

    private ConceptArgumentTests() {
    }

    private static String parseOrNull(String input) {
        try {
            return ConceptArgument.type().parse(new StringReader(input));
        } catch (CommandSyntaxException e) {
            return null;
        }
    }

    /**
     * The regression itself: a namespaced existence key must arrive <em>whole</em>.
     *
     * <p>Worth being precise about the old failure, because it was not an error. Brigadier's
     * {@code word()} does not reject a colon, it stops reading at one: the guardian key came back
     * as {@code "Existence_draconicevolution"} and {@code Contact_minecraft:zombie} came back as
     * {@code "Contact_minecraft"}. The command then reported a successful grant of that truncated
     * key, which matched no adaptation and did nothing. From the outside that is exactly "there is
     * no way to grant the adaptation I want" — no error, no symptom, just silence. The comparison
     * against {@code word()} is kept here so the reason this type exists stays visible.</p>
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void namespacedExistenceKeyParses(GameTestHelper helper) {
        String key = "Existence_draconicevolution:draconic_guardian";
        String parsed = parseOrNull(key);
        helper.assertTrue(parsed != null, key + " must be typeable, it carries a namespace");
        helper.assertTrue(key.equals(parsed), "the parser must return the key verbatim, got " + parsed);

        // And the behaviour that made this necessary, pinned so a refactor back to word() is visible.
        String viaWord;
        try {
            viaWord = com.mojang.brigadier.arguments.StringArgumentType.word()
                    .parse(new StringReader(key));
        } catch (CommandSyntaxException e) {
            viaWord = null;
        }
        helper.assertTrue(viaWord == null || !key.equals(viaWord),
                "word() is expected to mangle a namespaced key (got \"" + viaWord
                        + "\"); if Brigadier ever fixes that this test can be relaxed, but until "
                        + "then word() must not be used for concepts");
        helper.succeed();
    }

    /** The per-mob families are all namespaced, so all of them were unreachable. */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void perEntityFamiliesParse(GameTestHelper helper) {
        for (String key : new String[]{
                "Contact_minecraft:zombie",
                "Contact_minecraft:creeper",
                "Offense_NPC_minecraft:skeleton",
                "Drop_NPC_minecraft:spider",
                "Existence_minecraft:ender_dragon",
                "Debuff_minecraft:wither"}) {
            String parsed = parseOrNull(key);
            helper.assertTrue(parsed != null, key + " must be typeable");
            helper.assertTrue(key.equals(parsed), key + " must survive verbatim, got " + parsed);
        }
        helper.succeed();
    }

    /** Plain word-shaped keys must keep working exactly as before. */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void plainKeysStillParse(GameTestHelper helper) {
        for (String key : new String[]{"Type_FIRE", "Env_Lava", "Mutation_Fist", "Fist_Diamond",
                "Mine_Labor", "Self_Damage", "ADBERSITY", "Dimension_Destroy"}) {
            String parsed = parseOrNull(key);
            helper.assertTrue(key.equals(parsed), key + " must parse, got " + parsed);
        }
        helper.succeed();
    }

    /**
     * The parser must stop at whitespace, not swallow it. If it consumed spaces then a trailing
     * {@code <target>} player argument could never be reached, which would break
     * {@code /adaptionwheel grant Contact_minecraft:zombie Steve}.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void parserStopsAtWhitespace(GameTestHelper helper) {
        StringReader reader = new StringReader("Type_Fire Steve");
        String parsed = null;
        try {
            parsed = ConceptArgument.type().parse(reader);
        } catch (CommandSyntaxException e) {
            helper.assertTrue(false, "Type_Fire must parse: " + e.getMessage());
        }
        helper.assertTrue("Type_Fire".equals(parsed), "must read only the concept, got " + parsed);
        helper.assertTrue(!reader.canRead() || reader.peek() == ' ',
                "must leave the space in front of the next argument");
        helper.succeed();
    }

    /** An empty or immediately-delimited argument is a clean error, not a silent empty key. */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void emptyInputIsRejected(GameTestHelper helper) {
        helper.assertTrue(parseOrNull("") == null, "an empty concept must be rejected");
        helper.assertTrue(parseOrNull("   ") == null, "whitespace alone must be rejected");
        helper.succeed();
    }

    /**
     * Every concept the registry knows must be typeable. This is the test that would have caught
     * the original bug without anybody having to notice it by trying a boss command in game.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void everyRegisteredConceptIsTypeable(GameTestHelper helper) {
        List<AdaptationDefinition> all = AdaptationRegistry.allDefinitions();
        helper.assertTrue(!all.isEmpty(), "the registry must not be empty");
        for (AdaptationDefinition def : all) {
            helper.assertTrue(def.concept().equals(parseOrNull(def.concept())),
                    "registered concept " + def.concept() + " must be typeable");
        }
        helper.succeed();
    }

    /** The argument type must be usable where Brigadier expects one. */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void typeIsAnArgumentType(GameTestHelper helper) {
        ArgumentType<String> type = ConceptArgument.type();
        helper.assertTrue(type != null, "must expose an ArgumentType");
        helper.assertTrue(!type.getExamples().isEmpty(),
                "examples feed Brigadier's own error messages and should not be empty");
        helper.succeed();
    }
}
