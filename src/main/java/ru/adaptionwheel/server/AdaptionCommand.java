package ru.adaptionwheel.server;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.adapt.AdaptationDefinition;
import ru.adaptionwheel.adapt.AdaptationDomain;
import ru.adaptionwheel.adapt.AdaptationRegistry;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.data.PlayerAdaption;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Developer/debug tooling: inspect and manipulate the adaptation state.
 * Read subcommands are usable by any player on themselves; mutating
 * subcommands require permission level 2 (ops).
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID)
public final class AdaptionCommand {

    private AdaptionCommand() {
    }

    /** Suggests every registered concept plus dynamic per-entity/per-effect families. */
    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> CONCEPT_SUGGESTIONS =
            (ctx, builder) -> {
                for (AdaptationDefinition def : AdaptationRegistry.allDefinitions()) {
                    builder.suggest(def.concept());
                }
                for (String family : new String[]{
                        "Contact_", "Offense_NPC_", "Drop_NPC_", "Debuff_", "Existence_"}) {
                    builder.suggest(family);
                }
                return builder.buildFuture();
            };

    /** Suggests domain filters for /adaptionwheel list. */
    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> DOMAIN_SUGGESTIONS =
            (ctx, builder) -> {
                for (AdaptationDomain domain : AdaptationDomain.values()) {
                    builder.suggest(domain.getKey());
                }
                return builder.buildFuture();
            };

    /** Concept argument node with suggestions, reused by info/grant/ungrant/analyze. */
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String>
            conceptArg(String name) {
        return net.minecraft.commands.Commands.argument(name, ConceptArgument.type())
                .suggests(CONCEPT_SUGGESTIONS);
    }

    /**
     * Level argument. The upper bound tracks {@link PlayerAdaption#MAX_LEVEL} instead of being
     * written as a literal 8, so the command cannot quietly disagree with the data cap if that
     * constant ever moves.
     */
    private static final com.mojang.brigadier.arguments.IntegerArgumentType LEVEL_ARG =
            IntegerArgumentType.integer(0, PlayerAdaption.MAX_LEVEL);

    /** Sentinel meaning "no level was typed": max it for a leveled concept, ignore it otherwise. */
    private static final int LEVEL_DEFAULT = -1;

    private static int level(CommandContext<CommandSourceStack> ctx) {
        return IntegerArgumentType.getInteger(ctx, "level");
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = net.minecraft.commands.Commands
                .literal("adaptionwheel");

        // Bare /adaptionwheel prints usage so the tree is discoverable.
        root.executes(ctx -> {
            ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.usage"), false);
            ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.usage_hint"), false);
            return 1;
        });

        // ---- reads: self by default, optional explicit target ----
        root.then(net.minecraft.commands.Commands.literal("status")
                .executes(ctx -> status(ctx, self(ctx)))
                .then(net.minecraft.commands.Commands.argument("target", EntityArgument.player())
                        .requires(s -> s.hasPermission(2))
                        .executes(ctx -> status(ctx, EntityArgument.getPlayer(ctx, "target")))));

        root.then(net.minecraft.commands.Commands.literal("list")
                .executes(ctx -> list(ctx, self(ctx), null))
                .then(net.minecraft.commands.Commands.argument("domain", StringArgumentType.word())
                        .suggests(DOMAIN_SUGGESTIONS)
                        .executes(ctx -> list(ctx, self(ctx), StringArgumentType.getString(ctx, "domain")))
                        .then(net.minecraft.commands.Commands.argument("target", EntityArgument.player())
                                .requires(s -> s.hasPermission(2))
                                .executes(ctx -> list(ctx, EntityArgument.getPlayer(ctx, "target"),
                                        StringArgumentType.getString(ctx, "domain"))))));

        root.then(net.minecraft.commands.Commands.literal("info")
                .then(conceptArg("concept")
                        .executes(ctx -> {
                            ServerPlayer target;
                            try {
                                target = self(ctx);
                            } catch (Exception e) {
                                target = null; // console: show metadata only
                            }
                            return info(ctx, target, StringArgumentType.getString(ctx, "concept"));
                        })));

        // ---- mutations of state: ops only ----
        root.then(net.minecraft.commands.Commands.literal("grant")
                .requires(s -> s.hasPermission(2))
                .then(net.minecraft.commands.Commands.literal("all")
                        .executes(ctx -> grantAll(ctx, self(ctx), null))
                        .then(net.minecraft.commands.Commands.argument("target", EntityArgument.player())
                                .requires(s -> s.hasPermission(2))
                                .executes(ctx -> grantAll(ctx, EntityArgument.getPlayer(ctx, "target"), null)))
                        .then(conceptArg("domain")
                                .suggests(DOMAIN_SUGGESTIONS)
                                .executes(ctx -> grantAll(ctx, self(ctx), concept(ctx)))
                                .then(net.minecraft.commands.Commands.argument("target", EntityArgument.player())
                                        .requires(s -> s.hasPermission(2))
                                        .executes(ctx -> grantAll(ctx, EntityArgument.getPlayer(ctx, "target"),
                                                concept(ctx))))))
                .then(conceptArg("concept")
                        // No level given: max for a leveled concept, plain grant for a one-time one.
                        .executes(ctx -> grant(ctx, selfOrTarget(ctx, "target"), concept(ctx), -1))
                        .then(net.minecraft.commands.Commands.argument("level", LEVEL_ARG)
                                .executes(ctx -> grant(ctx, selfOrTarget(ctx, "target"), concept(ctx),
                                        level(ctx)))
                                .then(net.minecraft.commands.Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> grant(ctx, EntityArgument.getPlayer(ctx, "target"),
                                                concept(ctx), level(ctx)))))));

        root.then(net.minecraft.commands.Commands.literal("ungrant")
                .requires(s -> s.hasPermission(2))
                .then(conceptArg("concept")
                        .executes(ctx -> ungrant(ctx, selfOrTarget(ctx, "target"), concept(ctx)))
                        .then(net.minecraft.commands.Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> ungrant(ctx, EntityArgument.getPlayer(ctx, "target"),
                                        concept(ctx))))));

        root.then(net.minecraft.commands.Commands.literal("analyze")
                .requires(s -> s.hasPermission(2))
                .then(conceptArg("concept")
                        .executes(ctx -> analyze(ctx, selfOrTarget(ctx, "target"), concept(ctx)))
                        .then(net.minecraft.commands.Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> analyze(ctx, EntityArgument.getPlayer(ctx, "target"),
                                        concept(ctx))))));

        root.then(net.minecraft.commands.Commands.literal("reset")
                .requires(s -> s.hasPermission(2))
                .executes(ctx -> reset(ctx, self(ctx)))
                .then(net.minecraft.commands.Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> reset(ctx, EntityArgument.getPlayer(ctx, "target")))));

        root.then(net.minecraft.commands.Commands.literal("registry")
                .requires(s -> s.hasPermission(2))
                .executes(AdaptionCommand::registry));

        event.getDispatcher().register(root);
    }

    private static ServerPlayer self(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    /** Optional trailing target argument; falls back to the executing player. */
    private static ServerPlayer selfOrTarget(CommandContext<CommandSourceStack> ctx, String name)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try {
            return EntityArgument.getPlayer(ctx, name);
        } catch (IllegalArgumentException ignored) {
            // argument not present in this node
        }
        try {
            return ctx.getSource().getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw NO_PLAYER_ERROR.create();
        }
    }

    private static final com.mojang.brigadier.exceptions.SimpleCommandExceptionType NO_PLAYER_ERROR =
            new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                    Component.translatable("adaptionwheel.cmd.no_player"));

    private static String concept(CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "concept");
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
            // Raw components resolve on the receiving client, so names stay localized.
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
            // Console / no player: metadata only.
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

    /**
     * Grants one concept.
     *
     * <p>{@code level} is {@link #LEVEL_DEFAULT} when the operator did not type one. That is
     * resolved rather than defaulted up front, because the two concept kinds want opposite
     * treatment: a leveled concept wants its maximum, while a one-time concept has no level at
     * all and used to have a meaningless number written next to it in the command line.</p>
     */
    private static int grant(CommandContext<CommandSourceStack> ctx, ServerPlayer target,
                             String concept, int level) {
        if (!isPlausibleConcept(concept)) {
            reportUnknownConcept(ctx, concept);
            return 0;
        }
        boolean oneTime = Concepts.isOneTime(concept);
        if (oneTime && level != LEVEL_DEFAULT) {
            // Saying so beats silently discarding it, which is what the old command did.
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

    /**
     * Grants every registered concept, optionally limited to one domain. Leveled concepts go to
     * their maximum and one-time ones are simply set, which is what makes this a usable shortcut
     * for testing a late-game state instead of a hundred separate commands.
     *
     * @param domain a domain name, or {@code null} for every domain.
     */
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
        // Dynamic keys have no definition and cannot be enumerated; say so rather than let the
        // operator assume a Contact_<mob> they already earned was just wiped.
        final int count = granted;
        final String filterName = filter == null ? "all" : filter.getKey();
        ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.grant_all_done",
                count, filterName, target.getName()), true);
        return count;
    }

    /** Removes a single adaptation, the counterpart to {@code grant}. */
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

    /**
     * Rejects an unknown concept, and says what was probably meant. With a registry this size a
     * flat "unknown concept" is a dead end — the operator has to go and run {@code /registry}
     * and read through it. Offering close matches turns a dead end into a one-line fix.
     */
    private static void reportUnknownConcept(CommandContext<CommandSourceStack> ctx, String concept) {
        ctx.getSource().sendFailure(Component.translatable("adaptionwheel.cmd.unknown_concept", concept));
        List<String> near = nearMatches(concept);
        if (!near.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.did_you_mean",
                    String.join(", ", near)), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.use_registry"), false);
        }
    }

    /** Registered concepts containing {@code needle} case-insensitively, capped so chat stays readable. */
    private static List<String> nearMatches(String needle) {
        String lower = needle.toLowerCase(java.util.Locale.ROOT);
        List<String> hits = new ArrayList<>();
        for (AdaptationDefinition def : AdaptationRegistry.allDefinitions()) {
            if (def.concept().toLowerCase(java.util.Locale.ROOT).contains(lower)) {
                hits.add(def.concept());
                if (hits.size() >= 8) {
                    break;
                }
            }
        }
        return hits;
    }

    /**
     * Accepts registered definitions plus the dynamic per-entity/per-effect key families, which
     * are generated at runtime and so can never be in the registry. The prefix list is the full
     * set — it used to be missing several families, so a dynamic key outside the registered ones
     * was rejected outright instead of being accepted and doing nothing.
     */
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

    private static final String[] DYNAMIC_PREFIXES = {
            "Type_", "Env_", "Debuff_", "Contact_", "Offense_", "Drop_NPC_", "Existence_",
            "Mutation_", "Move_", "Percep_", "Mine_", "Combat_", "Phys_", "Fist_", "Dimension_"
    };

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
}
