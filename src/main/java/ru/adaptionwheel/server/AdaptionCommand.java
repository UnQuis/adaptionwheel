package ru.adaptionwheel.server;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.adapt.AdaptationDefinition;
import ru.adaptionwheel.adapt.AdaptationDomain;
import ru.adaptionwheel.adapt.AdaptationRegistry;
import ru.adaptionwheel.category.AdaptionCategory;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.data.ModDataComponents;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.data.WheelData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The /adaptionwheel command tree, and its /aw alias.
 *
 * <p>Two real bugs lived here before this revision, both in how the tree was wired rather than in
 * the command bodies themselves:
 *
 * <ul>
 *   <li>{@code grant all <domain>} read an argument named {@code "concept"} out of a context that
 *       only ever bound one named {@code "domain"}. That is not a typo that degrades gracefully --
 *       {@code CommandContext.getArgument} throws {@link IllegalArgumentException} for a name that
 *       was never bound, so every call to this branch crashed outright.</li>
 *   <li>{@code grant <concept> <target|level>} and {@code grant all <target|domain>} put two
 *       sibling argument nodes of different types in competition for the same next token. Nothing
 *       stops a player's name from being indistinguishable from a level number or a domain key as
 *       far as the parser is concerned, so which sibling wins was never something this code chose
 *       -- it was whatever Brigadier's internal tie-break happened to do. That is fixed here by
 *       disambiguating with an explicit keyword ({@code level}, {@code domain}) rather than relying
 *       on two argument types never colliding, which was never guaranteed in the first place.</li>
 * </ul>
 *
 * <p>The second fix changes two command shapes:
 * <pre>
 *   grant &lt;concept&gt; &lt;level&gt;        -&gt;  grant &lt;concept&gt; level &lt;level&gt;
 *   grant all &lt;domain&gt;                -&gt;  grant all domain &lt;domain&gt;
 * </pre>
 * Every other shape is unchanged. The rule going forward: no two sibling nodes under the same
 * parent may ever both be argument nodes capable of parsing the same token -- separate them with a
 * literal keyword, the way {@code level} and {@code domain} do above, the moment a second argument
 * sibling is added anywhere in this tree.
 *
 * <p>Every concept-taking subcommand ({@code info}, {@code grant}, {@code ungrant}, {@code shed},
 * {@code analyze}) now shares one selector set, wired once by {@link #addConceptSelectors}: the raw
 * quoted {@code <concept>}, {@code type <category>}, {@code env <name>},
 * {@code contact|offense|drop|existence <mob>} and {@code debuff <effect>}. The structured forms are
 * not sugar -- a concept key embeds a {@link ResourceLocation} ({@code Contact_minecraft:zombie}) and
 * a colon is not allowed in an unquoted Brigadier string, so the raw form has to be quoted. A player
 * who forgets the quote gets the key truncated at the colon, which used to look like a successful
 * grant of a concept that cannot exist. Handing the mob/effect half to a
 * {@link ResourceLocationArgument} keeps the id intact and lets the client receive registry-aware
 * suggestions.
 *
 * <p>The whole tree is registered twice, under {@code /adaptionwheel} and {@code /aw}, from the same
 * builder. A second hand-written tree would be a second thing to keep in step, and the alias is the
 * full tree rather than a stub.
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID)
public final class AdaptionCommand {

    private AdaptionCommand() {
    }

    /** How many suggestions the raw concept argument will offer before the list is truncated. */
    private static final int SUGGESTION_CAP = 80;

    /** Selector keyword to concept prefix, for the per-mob adaptation families. */
    private static final Map<String, String> ENTITY_FAMILIES = createEntityFamilies();

    /** The {@code type <category>} choices, built from the live category enum. */
    private static final List<AdaptionCategory> TYPE_CATEGORIES = buildTypeCategories();

    /** Selector keyword to concept key, for the one-time environments. */
    private static final Map<String, String> ENV_CHOICES = createEnvChoices();

    private static final SuggestionProvider<CommandSourceStack> CONCEPT_SUGGESTIONS =
            (ctx, builder) -> {
                String remaining = builder.getRemainingLowerCase();
                // The raw concept argument carries a leading quote when the player has already
                // started a quoted key, and the pool is matched against the key itself.
                if (remaining.startsWith("\"")) {
                    remaining = remaining.substring(1);
                }
                for (String id : conceptSuggestionIds(remaining)) {
                    // A bare family prefix is a fragment, not a concept: it has no display name and
                    // must never be quoted, or the player is told to type a key that cannot resolve.
                    boolean barePrefix = id.endsWith("_");
                    builder.suggest(needsQuotes(id) ? "\"" + id + "\"" : id,
                            barePrefix ? null : Concepts.displayName(id));
                }
                return builder.buildFuture();
            };

    private static final SuggestionProvider<CommandSourceStack> MOB_SUGGESTIONS =
            (ctx, builder) -> {
                for (String id : entitySuggestionIds(builder.getRemainingLowerCase())) {
                    builder.suggest(id);
                }
                return builder.buildFuture();
            };

    private static final SuggestionProvider<CommandSourceStack> EFFECT_SUGGESTIONS =
            (ctx, builder) -> {
                for (String id : effectSuggestionIds(builder.getRemainingLowerCase())) {
                    builder.suggest(id);
                }
                return builder.buildFuture();
            };

    private static final SuggestionProvider<CommandSourceStack> DOMAIN_SUGGESTIONS =
            (ctx, builder) -> {
                for (AdaptationDomain domain : AdaptationDomain.values()) {
                    builder.suggest(domain.getKey());
                }
                return builder.buildFuture();
            };

    /**
     * The type behind {@link #resolveConcept}'s failures.
     *
     * <p>Brigadier only accepts a {@link CommandSyntaxException} built from a type, and the message
     * is what vanilla prints, so the whole "unknown concept, did you mean ..." text rides on it
     * instead of being sent as a separate failure line that the caller could forget.
     */
    private static final SimpleCommandExceptionType DYNAMIC_ERROR =
            new SimpleCommandExceptionType(Component.empty());

    private static final int LEVEL_DEFAULT = -1;

    private static final IntegerArgumentType LEVEL_ARG =
            IntegerArgumentType.integer(0, PlayerAdaption.MAX_LEVEL);

    private static final SimpleCommandExceptionType NO_PLAYER_ERROR =
            new SimpleCommandExceptionType(Component.translatable("adaptionwheel.cmd.no_player"));

    private static final String[] DYNAMIC_PREFIXES = {
            "Type_", "Env_", "Debuff_", "Contact_", "Offense_", "Drop_NPC_", "Existence_",
            "Mutation_", "Move_", "Percep_", "Mine_", "Combat_", "Fist_", "Dimension_"
    };

    private static Map<String, String> createEntityFamilies() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("contact", Concepts.CONTACT_PREFIX);
        out.put("offense", Concepts.OFFENSE_PREFIX);
        out.put("drop", Concepts.DROP_PREFIX);
        out.put("existence", Concepts.EXISTENCE_PREFIX);
        return out;
    }

    /**
     * The {@code type <category>} choices.
     *
     * <p>Fire is not one of them: it stopped being a ladder when it became the one-time
     * {@link Concepts#ENV_FIRE}, and that is offered under {@code env fire} instead. Retired
     * categories are skipped for the same reason from the other direction -- their keys survive
     * only as legacy data with no definition behind them, so offering them would build a command
     * that grants nothing.
     */
    private static List<AdaptionCategory> buildTypeCategories() {
        List<AdaptionCategory> out = new ArrayList<>();
        for (AdaptionCategory category : AdaptionCategory.values()) {
            if (category == AdaptionCategory.FIRE || Concepts.isRetired(Concepts.type(category))) {
                continue;
            }
            out.add(category);
        }
        return out;
    }

    private static Map<String, String> createEnvChoices() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("fire", Concepts.ENV_FIRE);
        out.put("lava", Concepts.ENV_LAVA);
        out.put("fall", Concepts.ENV_FALL);
        out.put("drowning", Concepts.ENV_DROWN);
        out.put("thorns", Concepts.ENV_THORNS);
        out.put("darkness", Concepts.ENV_DARKNESS);
        out.put("suffocate", Concepts.ENV_SUFFOCATE);
        out.put("void", Concepts.ENV_VOID);
        out.put("starve", Concepts.ENV_STARVE);
        out.put("liquid", Concepts.ENV_LIQUID);
        out.put("inventory", Concepts.ENV_INVENTORY);
        out.put("ice", Concepts.ENV_ICE);
        out.put("slime", Concepts.ENV_SLIME);
        out.put("cobweb", Concepts.ENV_COBWEB);
        out.put("knockback", Concepts.ENV_KNOCKBACK);
        return out;
    }

    /**
     * Entity ids for the {@code <mob>} argument of a family selector.
     *
     * <p>Prefix first so that {@code zomb} lands on {@code minecraft:zombie} directly, then a
     * contains fallback once two characters have been typed -- that is what lets {@code skele}
     * find {@code minecraft:skeleton_horse}. Unquoted ids are the whole point of the structured
     * form, so nothing here is ever quoted.
     */
    public static List<String> entitySuggestionIds(String remaining) {
        String rem = remaining == null ? "" : remaining.toLowerCase(Locale.ROOT);
        List<ResourceLocation> keys = new ArrayList<>(BuiltInRegistries.ENTITY_TYPE.keySet());
        keys.sort(Comparator.comparing(ResourceLocation::toString));

        List<String> out = new ArrayList<>();
        for (ResourceLocation id : keys) {
            String full = id.toString();
            if (!rem.isEmpty() && !full.startsWith(rem) && !id.getPath().startsWith(rem)) {
                continue;
            }
            out.add(full);
        }
        if (out.isEmpty() && rem.length() >= 2) {
            for (ResourceLocation id : keys) {
                String full = id.toString();
                if (full.contains(rem) || id.getPath().contains(rem)) {
                    out.add(full);
                }
            }
        }
        return out;
    }

    /** Effect ids for the {@code <effect>} argument of {@code debuff}, with the same matching. */
    public static List<String> effectSuggestionIds(String remaining) {
        String rem = remaining == null ? "" : remaining.toLowerCase(Locale.ROOT);
        List<ResourceLocation> keys = new ArrayList<>(BuiltInRegistries.MOB_EFFECT.keySet());
        keys.sort(Comparator.comparing(ResourceLocation::toString));

        List<String> out = new ArrayList<>();
        for (ResourceLocation id : keys) {
            String full = id.toString();
            if (!rem.isEmpty() && !full.startsWith(rem) && !id.getPath().startsWith(rem)) {
                continue;
            }
            out.add(full);
        }
        if (out.isEmpty() && rem.length() >= 2) {
            for (ResourceLocation id : keys) {
                String full = id.toString();
                if (full.contains(rem) || id.getPath().contains(rem)) {
                    out.add(full);
                }
            }
        }
        return out;
    }

    /**
     * The suggestion pool for the raw concept argument.
     *
     * <p>Four sources, in the order a player expects them: registered concepts, the family
     * prefixes, concepts whose id or display name contains what was typed, and finally the
     * generated family-plus-mob ids -- {@code Contact_minecraft:zombie} and friends cannot be
     * enumerated up front because the registry mints them at runtime, so they are assembled from
     * the live entity and effect registries on every keystroke. The result is capped at
     * {@link #SUGGESTION_CAP}, because the generated pass alone can reach the hundreds.
     */
    public static List<String> conceptSuggestionIds(String remaining) {
        String rem = remaining == null ? "" : remaining.toLowerCase(Locale.ROOT);
        List<AdaptationDefinition> definitions = AdaptationRegistry.allDefinitions();
        Set<String> out = new LinkedHashSet<>();

        for (AdaptationDefinition def : definitions) {
            String id = def.concept();
            if (!rem.isEmpty() && !id.toLowerCase(Locale.ROOT).startsWith(rem)) {
                continue;
            }
            out.add(id);
        }
        for (String prefix : ENTITY_FAMILIES.values()) {
            if (!rem.isEmpty() && !prefix.toLowerCase(Locale.ROOT).startsWith(rem)) {
                continue;
            }
            out.add(prefix);
        }

        if (rem.length() >= 2) {
            for (AdaptationDefinition def : definitions) {
                String id = def.concept();
                if (id.toLowerCase(Locale.ROOT).contains(rem)) {
                    out.add(id);
                }
            }
            for (AdaptationDefinition def : definitions) {
                String id = def.concept();
                if (out.contains(id)
                        || !Concepts.displayName(id).getString().toLowerCase(Locale.ROOT).contains(rem)) {
                    continue;
                }
                out.add(id);
            }

            // "contact_mine" is a family plus half a mob id: keep the family and match only the
            // remainder, otherwise the prefix itself is what gets compared and nothing matches.
            String family = null;
            String body = rem;
            for (Map.Entry<String, String> entry : ENTITY_FAMILIES.entrySet()) {
                String lower = entry.getValue().toLowerCase(Locale.ROOT);
                if (!rem.startsWith(lower)) {
                    continue;
                }
                family = entry.getValue();
                body = rem.substring(lower.length());
                break;
            }

            for (ResourceLocation id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
                if (body.isEmpty() || id.getPath().contains(body) || id.toString().contains(body)) {
                    if (family != null) {
                        out.add(family + id);
                    } else {
                        for (String prefix : ENTITY_FAMILIES.values()) {
                            out.add(prefix + id);
                        }
                    }
                }
                if (out.size() > SUGGESTION_CAP) {
                    break;
                }
            }
            for (ResourceLocation id : BuiltInRegistries.MOB_EFFECT.keySet()) {
                if (id.getPath().contains(body) || id.toString().contains(body)) {
                    out.add(Concepts.debuff(id.toString()));
                }
                if (out.size() > SUGGESTION_CAP) {
                    break;
                }
            }
        }

        List<String> list = new ArrayList<>(out);
        return list.size() > SUGGESTION_CAP ? new ArrayList<>(list.subList(0, SUGGESTION_CAP)) : list;
    }

    /** Whether a suggested key has to be quoted to survive {@link StringArgumentType#string()}. */
    private static boolean needsQuotes(String id) {
        for (int i = 0; i < id.length(); i++) {
            if (!StringReader.isAllowedInUnquotedString(id.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Turns whatever the player typed into a concept key, or fails with the unknown-concept text.
     *
     * <p>Deliberately lenient, in this order: a plausible key as typed, a case-insensitive registry
     * key, the capitalised form ({@code env_fire} becomes {@code Env_Fire}), the historical
     * {@code offense_<mob>} spelling of {@code Offense_NPC_<mob>}, and finally a display name. The
     * display-name pass is what makes {@code grant Fire} work, because the panel shows names and
     * not keys.
     */
    private static String resolveConcept(String needle) throws CommandSyntaxException {
        String raw = needle.trim();
        if (!raw.isEmpty()) {
            if (isPlausibleConcept(raw)) {
                return raw;
            }
            for (AdaptationDefinition def : AdaptationRegistry.allDefinitions()) {
                if (def.concept().equalsIgnoreCase(raw)) {
                    return def.concept();
                }
            }
            String capped = Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
            if (!capped.equals(raw) && isPlausibleConcept(capped)) {
                return capped;
            }
            String lower = raw.toLowerCase(Locale.ROOT);
            if (lower.startsWith("offense_")) {
                String alternate = Concepts.OFFENSE_PREFIX + raw.substring("offense_".length());
                if (isPlausibleConcept(alternate)) {
                    return alternate;
                }
            }
            for (AdaptationDefinition def : AdaptationRegistry.allDefinitions()) {
                if (Concepts.displayName(def.concept()).getString().equalsIgnoreCase(raw)) {
                    return def.concept();
                }
            }
        }
        throw new CommandSyntaxException(DYNAMIC_ERROR, unknownConceptMessage(raw));
    }

    private static MutableComponent unknownConceptMessage(String raw) {
        MutableComponent message = Component.translatable("adaptionwheel.cmd.unknown_concept", raw);
        List<String> near = conceptSuggestionIds(raw.toLowerCase(Locale.ROOT));
        if (!near.isEmpty()) {
            List<String> few = near.size() > 8 ? near.subList(0, 8) : near;
            message.append(" ").append(Component.translatable("adaptionwheel.cmd.did_you_mean",
                    Component.literal(String.join(", ", few))));
        } else {
            message.append(" ").append(Component.translatable("adaptionwheel.cmd.use_registry"));
        }
        return message;
    }

    private static ResourceLocation requireEntity(ResourceLocation id) throws CommandSyntaxException {
        if (BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return id;
        }
        List<String> near = new ArrayList<>();
        for (ResourceLocation key : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            if (!key.getPath().contains(id.getPath()) || near.size() >= 8) {
                continue;
            }
            near.add(key.toString());
        }
        MutableComponent message = Component.translatable("adaptionwheel.cmd.unknown_entity", id.toString());
        if (!near.isEmpty()) {
            message.append(" ").append(Component.translatable("adaptionwheel.cmd.did_you_mean",
                    Component.literal(String.join(", ", near))));
        }
        throw new CommandSyntaxException(DYNAMIC_ERROR, message);
    }

    private static ResourceLocation requireEffect(ResourceLocation id) throws CommandSyntaxException {
        if (BuiltInRegistries.MOB_EFFECT.containsKey(id)) {
            return id;
        }
        List<String> near = new ArrayList<>();
        for (ResourceLocation key : BuiltInRegistries.MOB_EFFECT.keySet()) {
            if (!key.getPath().contains(id.getPath()) || near.size() >= 8) {
                continue;
            }
            near.add(key.toString());
        }
        MutableComponent message = Component.translatable("adaptionwheel.cmd.unknown_effect", id.toString());
        if (!near.isEmpty()) {
            message.append(" ").append(Component.translatable("adaptionwheel.cmd.did_you_mean",
                    Component.literal(String.join(", ", near))));
        }
        throw new CommandSyntaxException(DYNAMIC_ERROR, message);
    }

    private static int level(CommandContext<CommandSourceStack> ctx) {
        return IntegerArgumentType.getInteger(ctx, "level");
    }

    private static String domain(CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "domain");
    }

    private static String concept(CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "concept");
    }

    /**
     * Appends every concept selector, plus the raw {@code <concept>} string, to a parent node.
     *
     * <p>The tail says what may follow a selector; it never changes what the selector itself looks
     * like, so all five subcommands accept exactly the same set and the shapes cannot drift apart
     * one command at a time.
     *
     * <p>{@code targetNeedsPermission} is not the subcommand's own permission: {@code shed} is
     * deliberately open to any player for their own wheel, and only naming someone else costs
     * permission level 2. That is why the gate sits on the target node rather than on the parent.
     */
    private static void addConceptSelectors(LiteralArgumentBuilder<CommandSourceStack> parent, Tail tail,
                                            boolean targetNeedsPermission, ConceptRunner runner) {
        parent.then(withTail(conceptArg("concept"), tail, targetNeedsPermission, runner,
                ctx -> resolveConcept(concept(ctx))));

        LiteralArgumentBuilder<CommandSourceStack> type = Commands.literal("type");
        for (AdaptionCategory category : TYPE_CATEGORIES) {
            type.then(withTail(Commands.literal(category.getKey()), tail, targetNeedsPermission,
                    runner, ctx -> Concepts.type(category)));
        }
        parent.then(type);

        LiteralArgumentBuilder<CommandSourceStack> env = Commands.literal("env");
        for (Map.Entry<String, String> entry : ENV_CHOICES.entrySet()) {
            env.then(withTail(Commands.literal(entry.getKey()), tail, targetNeedsPermission,
                    runner, ctx -> entry.getValue()));
        }
        parent.then(env);

        for (Map.Entry<String, String> family : ENTITY_FAMILIES.entrySet()) {
            String prefix = family.getValue();
            parent.then(Commands.literal(family.getKey()).then(withTail(
                    Commands.argument("mob", ResourceLocationArgument.id()).suggests(MOB_SUGGESTIONS),
                    tail, targetNeedsPermission, runner,
                    ctx -> prefix + requireEntity(ResourceLocationArgument.getId(ctx, "mob")))));
        }

        parent.then(Commands.literal("debuff").then(withTail(
                Commands.argument("effect", ResourceLocationArgument.id()).suggests(EFFECT_SUGGESTIONS),
                tail, targetNeedsPermission, runner,
                ctx -> Concepts.debuff(requireEffect(ResourceLocationArgument.getId(ctx, "effect")).toString()))));
    }

    /**
     * Wires the argument tail a selector needs and hands the node back, so selectors stay one
     * expression each.
     */
    private static <B extends ArgumentBuilder<CommandSourceStack, B>> B withTail(
            B node, Tail tail, boolean targetNeedsPermission, ConceptRunner runner,
            ConceptExtractor extractor) {
        switch (tail) {
            case NONE -> node.executes(ctx ->
                    runner.run(ctx, selfOrNull(ctx), extractor.extract(ctx), LEVEL_DEFAULT));
            case TARGET -> {
                node.executes(ctx ->
                        runner.run(ctx, self(ctx), extractor.extract(ctx), LEVEL_DEFAULT));
                RequiredArgumentBuilder<CommandSourceStack, ?> target =
                        Commands.argument("target", EntityArgument.player());
                if (targetNeedsPermission) {
                    target.requires(s -> s.hasPermission(2));
                }
                node.then(target.executes(ctx -> runner.run(ctx,
                        EntityArgument.getPlayer(ctx, "target"), extractor.extract(ctx), LEVEL_DEFAULT)));
            }
            case TARGET_AND_LEVEL -> {
                node.executes(ctx ->
                        runner.run(ctx, self(ctx), extractor.extract(ctx), LEVEL_DEFAULT));
                node.then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> runner.run(ctx, EntityArgument.getPlayer(ctx, "target"),
                                extractor.extract(ctx), LEVEL_DEFAULT)));
                node.then(Commands.literal("level")
                        .then(Commands.argument("level", LEVEL_ARG)
                                .executes(ctx -> runner.run(ctx, self(ctx), extractor.extract(ctx), level(ctx)))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> runner.run(ctx,
                                                EntityArgument.getPlayer(ctx, "target"),
                                                extractor.extract(ctx), level(ctx))))));
            }
        }
        return node;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(buildRoot("adaptionwheel"));
        event.getDispatcher().register(buildRoot("aw"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRoot(String name) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name);

        root.executes(ctx -> {
            ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.usage"), false);
            ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.usage_hint"), false);
            return 1;
        });

        root.then(Commands.literal("status")
                .executes(ctx -> status(ctx, self(ctx)))
                .then(Commands.argument("target", EntityArgument.player())
                        .requires(s -> s.hasPermission(2))
                        .executes(ctx -> status(ctx, EntityArgument.getPlayer(ctx, "target")))));

        root.then(Commands.literal("list")
                .executes(ctx -> list(ctx, self(ctx), null))
                .then(domainArg("domain")
                        .executes(ctx -> list(ctx, self(ctx), domain(ctx)))
                        .then(Commands.argument("target", EntityArgument.player())
                                .requires(s -> s.hasPermission(2))
                                .executes(ctx -> list(ctx, EntityArgument.getPlayer(ctx, "target"),
                                        domain(ctx))))));

        LiteralArgumentBuilder<CommandSourceStack> infoNode = Commands.literal("info");
        addConceptSelectors(infoNode, Tail.NONE, false,
                (ctx, target, concept, lvl) -> info(ctx, target, concept));
        root.then(infoNode);

        // grant <concept>                       -> self, default level
        // grant <concept> <target>               -> target, default level
        // grant <concept> level <level>          -> self, explicit level
        // grant <concept> level <level> <target> -> target, explicit level
        //
        // "level" is a literal keyword, not a second argument sibling of "target": a player can
        // legally be named anything an integer can also look like, so a bare integer argument and
        // a bare player argument must never compete for the same token. See the class javadoc.
        LiteralArgumentBuilder<CommandSourceStack> grantNode = Commands.literal("grant")
                .requires(s -> s.hasPermission(2));
        grantNode.then(Commands.literal("all")
                .executes(ctx -> grantAll(ctx, self(ctx), null))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> grantAll(ctx, EntityArgument.getPlayer(ctx, "target"), null)))
                .then(Commands.literal("domain")
                        .then(domainArg("domain")
                                .executes(ctx -> grantAll(ctx, self(ctx), domain(ctx)))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> grantAll(ctx,
                                                EntityArgument.getPlayer(ctx, "target"), domain(ctx)))))));
        addConceptSelectors(grantNode, Tail.TARGET_AND_LEVEL, false,
                (ctx, target, concept, lvl) -> grant(ctx, target, concept, lvl));
        root.then(grantNode);

        LiteralArgumentBuilder<CommandSourceStack> ungrantNode = Commands.literal("ungrant")
                .requires(s -> s.hasPermission(2));
        addConceptSelectors(ungrantNode, Tail.TARGET, false,
                (ctx, target, concept, lvl) -> ungrant(ctx, target, concept));
        root.then(ungrantNode);

        LiteralArgumentBuilder<CommandSourceStack> shedNode = Commands.literal("shed");
        addConceptSelectors(shedNode, Tail.TARGET, true,
                (ctx, target, concept, lvl) -> shed(ctx, target, concept));
        root.then(shedNode);

        LiteralArgumentBuilder<CommandSourceStack> analyzeNode = Commands.literal("analyze")
                .requires(s -> s.hasPermission(2));
        addConceptSelectors(analyzeNode, Tail.TARGET, false,
                (ctx, target, concept, lvl) -> analyze(ctx, target, concept));
        root.then(analyzeNode);

        root.then(Commands.literal("reset")
                .requires(s -> s.hasPermission(2))
                .executes(ctx -> reset(ctx, self(ctx)))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> reset(ctx, EntityArgument.getPlayer(ctx, "target")))));

        root.then(Commands.literal("registry")
                .requires(s -> s.hasPermission(2))
                .executes(AdaptionCommand::registry));

        root.then(Commands.literal("debug")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("aggro")
                        .executes(ctx -> aggro(ctx, self(ctx))))
                .then(Commands.literal("altar")
                        .executes(ctx -> altarLookup(ctx, null))
                        .then(Commands.argument("item", StringArgumentType.greedyString())
                                .executes(ctx -> altarLookup(ctx,
                                        StringArgumentType.getString(ctx, "item")))))
                .then(Commands.literal("flight")
                        .executes(ctx -> flight(ctx, self(ctx)))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> flight(ctx, selfOrTarget(ctx, "player")))))
                .then(Commands.literal("wheel")
                        .executes(ctx -> wheel(ctx, self(ctx)))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> wheel(ctx, selfOrTarget(ctx, "player"))))));

        return root;
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> conceptArg(String name) {
        return Commands.argument(name, StringArgumentType.string())
                .suggests(CONCEPT_SUGGESTIONS);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> domainArg(String name) {
        return Commands.argument(name, StringArgumentType.word())
                .suggests(DOMAIN_SUGGESTIONS);
    }

    private static ServerPlayer self(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    /**
     * The source player, or null when the command was run from the console.
     *
     * <p>Only {@code info} uses this: it is the one subcommand that can answer without a player
     * ("what is this concept and what does it cap at"), and refusing to answer from the console
     * would be a limitation with nothing behind it.
     */
    @Nullable
    private static ServerPlayer selfOrNull(CommandContext<CommandSourceStack> ctx) {
        try {
            return ctx.getSource().getPlayerOrException();
        } catch (CommandSyntaxException e) {
            return null;
        }
    }

    private static ServerPlayer selfOrTarget(CommandContext<CommandSourceStack> ctx, String name)
            throws CommandSyntaxException {
        try {
            return EntityArgument.getPlayer(ctx, name);
        } catch (IllegalArgumentException ignored) {
            // No such argument was bound on this path, meaning this branch is the self-only one.
        }
        try {
            return ctx.getSource().getPlayerOrException();
        } catch (CommandSyntaxException e) {
            throw NO_PLAYER_ERROR.create();
        }
    }

    private static int status(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        PlayerAdaption data = AdaptionEvents.dataOf(target);
        ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.status",
                target.getName(),
                data.getAdaptCount(),
                data.adversityActive ? "ACTIVE" : "idle",
                data.adversityCooldownTimer / 20,
                data.tasks.size()), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx, ServerPlayer target, String domainFilter) {
        PlayerAdaption data = AdaptionEvents.dataOf(target);
        AdaptationDomain filter = parseDomain(domainFilter);

        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(data.adapted);
        keys.addAll(data.levels.keySet());
        List<String> sorted = new ArrayList<>(keys);
        sorted.sort(Comparator
                .comparingInt((String k) -> AdaptationRegistry.domainOf(k).ordinal())
                .thenComparing(Comparator.naturalOrder()));

        MutableComponent header = Component.translatable("adaptionwheel.cmd.list_header",
                target.getName(), sorted.size());
        if (filter != null) {
            header.append(" [" + filter.getKey() + "]");
        }
        ctx.getSource().sendSuccess(() -> header, false);

        int shown = 0;
        for (String key : sorted) {
            if (filter != null && AdaptationRegistry.domainOf(key) != filter) {
                continue;
            }
            String state;
            if (data.isAdapted(key) && !Concepts.isLevelBased(key)) {
                state = "ADAPTED";
            } else {
                int level = data.level(key);
                state = level >= PlayerAdaption.MAX_LEVEL ? "MAX" : "Lv." + level;
            }
            final String fKey = key;
            final String fState = state;

            ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.list_entry",
                    Concepts.displayName(fKey), domainTag(fKey), fState), false);
            shown++;
        }
        if (shown == 0) {
            ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.list_empty"), false);
        }
        return shown;
    }

    private static String domainTag(String concept) {
        return AdaptationRegistry.domainOf(concept).getKey();
    }

    private static AdaptationDomain parseDomain(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        for (AdaptationDomain domain : AdaptationDomain.values()) {
            if (domain.getKey().equalsIgnoreCase(raw)) {
                return domain;
            }
        }
        return null;
    }

    private static int info(CommandContext<CommandSourceStack> ctx, ServerPlayer target, String concept) {
        AdaptationDefinition def = AdaptationRegistry.get(concept);
        boolean oneTime = def != null ? !def.leveled() : Concepts.isOneTime(concept);
        int maxLevel = def != null ? def.maxLevel() : PlayerAdaption.MAX_LEVEL;
        if (target == null) {
            final String kind = oneTime ? "one-time" : "leveled 1-" + maxLevel;
            ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.info",
                    Concepts.displayName(concept),
                    concept,
                    Component.literal("-"),
                    Component.literal("-"),
                    "[" + domainTag(concept) + "] " + kind), false);
            return 1;
        }
        PlayerAdaption data = AdaptionEvents.dataOf(target);
        int level = data.level(concept);
        String state = oneTime ? (data.isAdapted(concept)
                                  ? Component.translatable("adaptionwheel.gui.adapted").getString() : "-")
                : level + "/" + maxLevel + (level >= maxLevel ? " (" + Component.translatable("adaptionwheel.gui.max_reached").getString() + ")" : "");
        ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.info",
                Concepts.displayName(concept),
                concept,
                oneTime ? Component.literal("-") : Component.literal(String.valueOf(level)),
                oneTime ? Component.literal("-") : Component.literal(String.valueOf(maxLevel)),
                state), false);
        return 1;
    }

    private static int grant(CommandContext<CommandSourceStack> ctx, ServerPlayer target,
                             String concept, int level) {
        Component truncated = truncationHint(concept);
        if (truncated != null) {
            ctx.getSource().sendFailure(truncated);
            return 0;
        }
        if (!isPlausibleConcept(concept)) {
            ctx.getSource().sendFailure(unknownConceptMessage(concept));
            return 0;
        }
        boolean oneTime = Concepts.isOneTime(concept);
        if (oneTime && level != LEVEL_DEFAULT) {
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "adaptionwheel.cmd.grant_level_ignored", concept), false);
        }
        int effective = oneTime ? PlayerAdaption.MAX_LEVEL
                : (level == LEVEL_DEFAULT ? PlayerAdaption.MAX_LEVEL : level);
        AdaptionEvents.debugGrant(target, concept, effective);
        String state = oneTime ? "adapted" : "Lv." + effective;
        ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.grant_done",
                concept, state, target.getName()), true);
        return 1;
    }

    private static int grantAll(CommandContext<CommandSourceStack> ctx, ServerPlayer target,
                                String domain) {
        AdaptationDomain filter = parseDomain(domain);
        int granted = 0;
        for (AdaptationDefinition def : AdaptationRegistry.allDefinitions()) {
            if (filter != null && def.domain() != filter) {
                continue;
            }
            AdaptionEvents.debugGrant(target, def.concept(),
                    def.leveled() ? def.maxLevel() : PlayerAdaption.MAX_LEVEL);
            granted++;
        }

        final int count = granted;
        final String filterName = filter == null ? "all" : filter.getKey();
        ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.grant_all_done",
                count, filterName, target.getName()), true);
        return count;
    }

    private static int shed(CommandContext<CommandSourceStack> ctx, ServerPlayer target,
                            String concept) {
        Shedding.Refusal refusal = Shedding.shed(target, AdaptionEvents.dataOf(target), concept);
        if (refusal != Shedding.Refusal.OK) {
            ctx.getSource().sendFailure(Component.translatable(
                    "adaptionwheel.cmd.shed_" + refusal.name().toLowerCase(Locale.ROOT),
                    concept));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.shed_done",
                concept, target.getName()), true);
        return 1;
    }

    private static int ungrant(CommandContext<CommandSourceStack> ctx, ServerPlayer target,
                               String concept) {
        if (!AdaptionEvents.debugUngrant(target, concept)) {
            ctx.getSource().sendFailure(Component.translatable("adaptionwheel.cmd.ungrant_absent",
                    concept, target.getName()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.ungrant_done",
                concept, target.getName()), true);
        return 1;
    }

    @Nullable
    private static Component truncationHint(String concept) {
        for (String namespace : knownNamespaces()) {
            if (concept.endsWith("_" + namespace)) {
                return Component.translatable("adaptionwheel.cmd.looks_truncated",
                        concept, concept + ":...");
            }
        }
        return null;
    }

    private static Set<String> knownNamespaces() {
        Set<String> out = new LinkedHashSet<>();
        out.add("minecraft");
        try {
            for (var mod : ModList.get().getMods()) {
                out.add(mod.getModId());
            }
        } catch (Throwable ignored) {
            // ModList not ready yet (e.g. very early datagen context); namespace hinting is
            // best-effort only, so falling back to just "minecraft" is fine.
        }
        return out;
    }

    private static boolean isPlausibleConcept(String concept) {
        if (AdaptationRegistry.isRegistered(concept)) {
            return true;
        }
        for (String prefix : DYNAMIC_PREFIXES) {
            if (concept.startsWith(prefix)) {
                return true;
            }
        }
        return concept.equals(Concepts.SELF_DAMAGE) || concept.equals(Concepts.ADVERSITY);
    }

    private static int analyze(CommandContext<CommandSourceStack> ctx, ServerPlayer target, String concept) {
        PlayerAdaption data = AdaptionEvents.dataOf(target);
        int ticks = (int) (AdaptionConfig.DEFENSE_ANALYSIS_SECONDS.get() * 20);
        AdaptionEvents.startTask(target, data, concept, ticks);
        ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.analyze_done",
                concept, target.getName()), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        AdaptionEvents.debugReset(target);
        ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.reset_done",
                target.getName()), true);
        return 1;
    }

    private static int aggro(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = (ServerLevel) target.level();
        source.sendSuccess(() -> Component.literal("— why mobs do or do not target "
                + target.getName().getString() + " —"), false);
        source.sendSuccess(() -> Component.literal("difficulty: " + level.getDifficulty()
                + "   (peaceful makes every hostile mob passive)"), false);

        source.sendSuccess(() -> Component.literal("gamemode: " + target.gameMode.getGameModeForPlayer()
                + "   abilities.invulnerable=" + target.getAbilities().invulnerable), false);
        source.sendSuccess(() -> Component.literal("canBeSeenByAnyone: " + target.canBeSeenByAnyone()
                + "   isSpectator=" + target.isSpectator() + "   isAlive=" + target.isAlive()), false);
        source.sendSuccess(() -> Component.literal("canBeSeenAsEnemy: " + target.canBeSeenAsEnemy()
                + "   isInvulnerable=" + target.isInvulnerable()
                + "   isInvisible=" + target.isInvisible()), false);
        if (!target.canBeSeenAsEnemy()) {
            source.sendSuccess(() -> Component.literal("=> NO MOB CAN TARGET THIS PLAYER. "
                    + "Creative, spectator, dead, or explicitly invulnerable."), false);
        }
        String effects = target.getActiveEffects().stream()
                .map(effect -> effect.getEffect().getRegisteredName() + " " + (effect.getAmplifier() + 1))
                .reduce((a, b) -> a + ", " + b).orElse("(none)");
        source.sendSuccess(() -> Component.literal("effects: " + effects), false);

        List<Mob> hostile = level.getEntitiesOfClass(
                Mob.class, target.getBoundingBox().inflate(24.0D),
                mob -> mob instanceof Enemy);
        source.sendSuccess(() -> Component.literal("hostile mobs within 24 blocks: " + hostile.size()), false);
        int shown = 0;
        for (Mob mob : hostile) {
            if (shown++ >= 5) {
                source.sendSuccess(() -> Component.literal("... and " + (hostile.size() - 5) + " more"), false);
                break;
            }
            var mobTarget = mob.getTarget();
            source.sendSuccess(() -> Component.literal("  " + mob.getName().getString() + " -> "
                    + (mobTarget == null ? "(nothing)"
                    : mobTarget.getName().getString() + " at "
                      + String.format(Locale.ROOT, "%.1f", mob.distanceTo(target)) + " blocks")), false);
        }
        return 1;
    }

    /**
     * Everything about where a wheel's data actually lives, in one place.
     *
     * <p>Written because "my adaptations reset" has four plausible owners — the attachment, the item's
     * {@code wheel_data} component, the merge on equip, and the login path — and comparing them by
     * reading source is how you end up fixing the wrong one. This prints all four numbers.
     */
    private static int wheel(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        CommandSourceStack source = ctx.getSource();
        PlayerAdaption data = AdaptionEvents.dataOf(target);
        boolean wearing = AdaptionEvents.isWearingWheel(target);
        source.sendSuccess(() -> Component.literal("— wheel data: " + target.getName().getString() + " —"), false);
        source.sendSuccess(() -> Component.literal("worn=" + wearing
                + " wasWearing=" + data.wasWearing
                + " equippedStack=" + (data.equippedStack == null ? "null" : "tracked")), false);

        WheelData onItem = AdaptionEvents.getWheelStack(target)
                .map(s -> s.get(ModDataComponents.WHEEL_DATA))
                .orElse(null);
        source.sendSuccess(() -> Component.literal("attachment: adapted=" + data.adapted.size()
                + " levels=" + data.levels.size()
                + " count=" + data.getAdaptCount()
                + " tasks=" + data.tasks.size()
                + " disabled=" + data.disabled.size()
                + " cache=" + data.cache.size()), false);
        source.sendSuccess(() -> Component.literal("wheel item: " + (onItem == null ? "no wheel_data"
                        : "adapted=" + onItem.adapted().size()
                        + " levels=" + onItem.levels().size()
                        + " count=" + onItem.adaptCount()
                        + " tasks=" + onItem.tasks().size())), false);

        if (onItem != null) {
            int onlyOnItem = 0;
            for (String concept : onItem.adapted()) {
                if (!data.adapted.contains(concept)) {
                    onlyOnItem++;
                }
            }
            int onlyInAttachment = 0;
            for (String concept : data.adapted) {
                if (!onItem.adapted().contains(concept)) {
                    onlyInAttachment++;
                }
            }
            int itemOnly = onlyOnItem;
            int attachmentOnly = onlyInAttachment;
            source.sendSuccess(() -> Component.literal("only on the item=" + itemOnly
                    + "   only in the attachment=" + attachmentOnly
                    + "   (a login merges the item into the attachment, so only-on-item is a repair)"), false);
        }

        source.sendSuccess(() -> Component.literal("ability list to client: "
                + (DiveAbility.unlocked(data) ? DiveAbility.ID : "(none)")), false);
        source.sendSuccess(() -> Component.literal("dive gate: "
                + Concepts.ENV_FALL + " adapted=" + data.isAdapted(Concepts.ENV_FALL)
                + " enabled=" + data.isEnabled(Concepts.ENV_FALL)
                + " | " + Concepts.MUTATION_FLIGHT + " adapted=" + data.isAdapted(Concepts.MUTATION_FLIGHT)
                + " enabled=" + data.isEnabled(Concepts.MUTATION_FLIGHT)), false);
        return 1;
    }

    private static int flight(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        CommandSourceStack source = ctx.getSource();
        PlayerAdaption data = AdaptionEvents.dataOf(target);
        boolean wearing = AdaptionEvents.isWearingWheel(target);
        source.sendSuccess(() -> Component.literal("— flight: " + target.getName().getString() + " —"), false);

        source.sendSuccess(() -> Component.literal("wheel worn: " + wearing
                + "   mutations.flight config=" + AdaptionConfig.ENABLE_MUTATION_FLIGHT.get()), false);

        String concept = Concepts.MUTATION_FLIGHT;
        source.sendSuccess(() -> Component.literal(Concepts.chatName(concept)
                + ": adapted=" + data.isAdapted(concept) + " enabled=" + data.isEnabled(concept)), false);

        WheelData onItem = AdaptionEvents.getWheelStack(target)
                .map(s -> s.get(ModDataComponents.WHEEL_DATA))
                .orElse(null);
        int itemCount = onItem == null ? -1 : onItem.adaptCount();
        source.sendSuccess(() -> Component.literal("attachment adaptCount=" + data.getAdaptCount()
                + "   wheel item wheel_data adaptCount=" + (itemCount < 0 ? "(no component)" : itemCount)), false);
        if (wearing && itemCount >= 0 && itemCount != data.getAdaptCount()) {
            source.sendSuccess(() -> Component.literal(">>> THESE DIFFER. Equipping merges the wheel item into the "
                    + "attachment (levels take the higher value, sets are unioned), so the lower side is what the "
                    + "next save will write back to the item."), false);
        }
        if (onItem != null && itemCount > 0
                && !onItem.adapted().contains(concept) && data.isAdapted(concept)) {
            source.sendSuccess(() -> Component.literal(">>> " + Concepts.chatName(concept)
                    + " is in the attachment but NOT on the wheel item: the item's copy is the one that "
                    + "travels, so this is repaired on the next save, not lost."), false);
        }

        double altitude = target.getY();
        double needed = AdaptionConfig.FLIGHT_ALTITUDE.get();
        int phantom = data.level(Concepts.contact("minecraft:phantom"));
        boolean levitation = data.isAdapted(Concepts.debuff("minecraft:levitation"));
        source.sendSuccess(() -> Component.literal("unlock: y=" + String.format(Locale.ROOT, "%.1f", altitude)
                + " (needs " + needed + ") " + mark(altitude >= needed)
                + "   " + Concepts.contact("minecraft:phantom") + "=" + phantom + "/"
                + PlayerAdaption.MAX_LEVEL + " " + mark(phantom >= PlayerAdaption.MAX_LEVEL)
                + "   " + Concepts.debuff("minecraft:levitation") + "=" + levitation + " " + mark(levitation)), false);

        source.sendSuccess(() -> Component.literal("gamemode=" + target.gameMode.getGameModeForPlayer()
                + " isCreative=" + target.isCreative() + " isSpectator=" + target.isSpectator()), false);
        source.sendSuccess(() -> Component.literal("mayfly=" + target.getAbilities().mayfly
                + " flying=" + target.getAbilities().flying
                + " flySpeed=" + target.getAbilities().getFlyingSpeed()), false);

        if (!wearing) {
            source.sendSuccess(() -> Component.literal("=> NOT WORN: tickFlight is never reached."), false);
        } else if (!AdaptionConfig.ENABLE_MUTATION_FLIGHT.get()) {
            source.sendSuccess(() -> Component.literal("=> mutations.flight is off in the config."), false);
        } else if (!data.isAdapted(concept)) {
            source.sendSuccess(() -> Component.literal("=> NOT ADAPTED yet: it is granted the first tick you are at y="
                    + needed + " with the other two conditions true."), false);
        } else if (target.isCreative() || target.isSpectator()) {
            source.sendSuccess(() -> Component.literal("=> creative/spectator: tickFlight returns early and never sets "
                    + "mayfly. Creative already flies, so this is invisible there — test in SURVIVAL."), false);
        } else if (!data.isEnabled(concept)) {
            source.sendSuccess(() -> Component.literal("=> DISABLED in the adaptation panel, so mayfly is revoked."), false);
        } else if (!target.getAbilities().mayfly) {
            source.sendSuccess(() -> Component.literal("=> adapted, enabled, worn, survival — and mayfly is still false. "
                    + "That is a bug; the two lines above the gate are where to look."), false);
        } else if (!target.getAbilities().flying) {
            source.sendSuccess(() -> Component.literal("=> mayfly is SET but flying is not. "
                    + "tickFlight re-asserts both every tick, so this should not survive a tick."), false);
        } else {
            source.sendSuccess(() -> Component.literal("=> mayfly AND flying are both SET: the player is flying."), false);
        }
        return 1;
    }

    private static String mark(boolean ok) {
        return ok ? "OK" : "NO";
    }

    private static int altarLookup(CommandContext<CommandSourceStack> ctx, String itemId) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        if (itemId == null) {
            int mobs = AltarOfferings.allMobs(server).size();
            source.sendSuccess(() -> Component.literal(mobs
                    + " mobs have loot here. /aw debug altar <item> for one item."), false);
            return mobs;
        }
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            source.sendSuccess(() -> Component.literal("no such item: " + itemId), false);
            return 0;
        }
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
        List<String> mobs = AltarOfferings.mobsFor(stack, server);
        source.sendSuccess(() -> Component.literal(itemId + " opens: "
                + (mobs.isEmpty() ? "(nothing - not an offering)"
                : String.join(", ", mobs))), false);
        DomainExchange.Recipe recipe = DomainExchange.recipeFor(stack);
        if (recipe != null) {
            source.sendSuccess(() -> Component.literal("  price list: " + recipe.itemsPerTrade()
                    + " item(s) for the first level, selectors "
                    + recipe.selectors().stream()
                    .map(DomainExchange.Selector::text)
                    .reduce((a, b) -> a + ", " + b).orElse("?")), false);
        }
        for (String mob : mobs) {
            source.sendSuccess(() -> Component.literal("  " + Concepts.offense(mob) + ", "
                    + Concepts.drop(mob)), false);
        }
        return Math.max(1, mobs.size());
    }

    private static int registry(CommandContext<CommandSourceStack> ctx) {
        List<AdaptationDefinition> all = AdaptationRegistry.allDefinitions();
        ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.registry_header", all.size()), false);
        for (AdaptationDefinition def : all) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                            "- " + def.concept() + " [" + def.domain().getKey() + "]"
                                    + (def.leveled() ? " Lv1-" + def.maxLevel() : " one-time"))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(def.domain().getColor()))), false);
        }
        return all.size();
    }

    /** What may follow a concept selector. */
    private enum Tail {
        /** Nothing: the selector is the last token. */
        NONE,
        /** An optional {@code <target>} player. */
        TARGET,
        /** An optional {@code <target>} player and an optional {@code level <level>} tail. */
        TARGET_AND_LEVEL
    }

    @FunctionalInterface
    private interface ConceptExtractor {

        String extract(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }

    @FunctionalInterface
    private interface ConceptRunner {

        int run(CommandContext<CommandSourceStack> ctx, ServerPlayer target, String concept, int level)
                throws CommandSyntaxException;
    }
}
