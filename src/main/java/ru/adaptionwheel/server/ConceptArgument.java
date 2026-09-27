package ru.adaptionwheel.server;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A concept argument that tolerates a namespace separator.
 *
 * <p>Concept keys are not all bare words. Any key built from a namespaced id carries a colon:
 * the Chaos Guardian's existence key is {@code Existence_draconicevolution:draconic_guardian},
 * and every {@code Contact_<mob>} / {@code Offense_NPC_<mob>} / {@code Drop_NPC_<mob>} family
 * uses a full {@code namespace:path}. {@code StringArgumentType.word()} accepts only
 * {@code 0-9 A-Z a-z _ - . +} — and it does not <em>reject</em> a colon, it stops reading at one.
 * The guardian key came back as {@code "Existence_draconicevolution"}, the command reported a
 * successful grant of that key, and nothing happened. No error, no symptom, so from the outside
 * there was simply no way to grant those adaptations.
 *
 * <p>Quoting is not an acceptable answer: the concept is only one argument in a chain that ends
 * in an optional player target, and forcing quotes on the one argument that most needs to be
 * typed freely makes the command worse to use, not better. This parser reads the same character
 * set as {@code word()} plus {@code :} and {@code /}, so every concept is typeable unquoted
 * while a trailing target argument still parses.
 */
public final class ConceptArgument {

    /** {@code word()}'s set ({@code _ - . +}) plus the namespace separator and path slash. */
    private static final String EXTRA_CHARS = "_.+:/";

    private ConceptArgument() {
    }

    public static ArgumentType<String> type() {
        return new ConceptType();
    }

    private static boolean isAllowed(char c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || EXTRA_CHARS.indexOf(c) >= 0;
    }

    private static final SimpleCommandExceptionType EMPTY =
            new SimpleCommandExceptionType(Component.translatable("adaptionwheel.cmd.concept_empty"));

    private static final class ConceptType implements ArgumentType<String> {

        @Override
        public String parse(StringReader reader) throws CommandSyntaxException {
            int start = reader.getCursor();
            while (reader.canRead() && isAllowed(reader.peek())) {
                reader.skip();
            }
            String value = reader.getString().substring(start, reader.getCursor());
            if (value.isEmpty()) {
                throw EMPTY.createWithContext(reader);
            }
            return value;
        }

        @Override
        public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context,
                                                                 SuggestionsBuilder builder) {
            return builder.buildFuture();
        }

        @Override
        public Collection<String> getExamples() {
            return List.of(ru.adaptionwheel.category.Concepts.MUTATION_FIST,
                    "Existence_draconicevolution:draconic_guardian",
                    "Contact_minecraft:zombie");
        }
    }
}
