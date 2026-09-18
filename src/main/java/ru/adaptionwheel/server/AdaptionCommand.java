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

    /** Concept argument node with suggestions, reused by info/grant/analyze. */
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String>
            conceptArg(String name) {
        return net.minecraft.commands.Commands.argument(name, StringArgumentType.word())
                .suggests(CONCEPT_SUGGESTIONS);
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
                        .requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> status(ctx, EntityArgument.getPlayer(ctx, "target")))));

        root.then(net.minecraft.commands.Commands.literal("list")
                .executes(ctx -> list(ctx, self(ctx), null))
                .then(net.minecraft.commands.Commands.argument("domain", StringArgumentType.word())
                        .suggests(DOMAIN_SUGGESTIONS)
                        .executes(ctx -> list(ctx, self(ctx), StringArgumentType.getString(ctx, "domain")))
                        .then(net.minecraft.commands.Commands.argument("target", EntityArgument.player())
                                .requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
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
                .requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                .then(conceptArg("concept")
                        .executes(ctx -> grant(ctx, selfOrTarget(ctx, "target"), concept(ctx), PlayerAdaption.MAX_LEVEL))
                        .then(net.minecraft.commands.Commands.argument("level", IntegerArgumentType.integer(0, 8))
                                .executes(ctx -> grant(ctx, selfOrTarget(ctx, "target"), concept(ctx),
                                        IntegerArgumentType.getInteger(ctx, "level")))
                                .then(net.minecraft.commands.Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> grant(ctx, EntityArgument.getPlayer(ctx, "target"),
                                                concept(ctx), IntegerArgumentType.getInteger(ctx, "level")))))));

        root.then(net.minecraft.commands.Commands.literal("analyze")
                .requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                .then(conceptArg("concept")
                        .executes(ctx -> analyze(ctx, selfOrTarget(ctx, "target"), concept(ctx)))
                        .then(net.minecraft.commands.Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> analyze(ctx, EntityArgument.getPlayer(ctx, "target"),
                                        concept(ctx))))));

        root.then(net.minecraft.commands.Commands.literal("reset")
                .requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> reset(ctx, self(ctx)))
                .then(net.minecraft.commands.Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> reset(ctx, EntityArgument.getPlayer(ctx, "target")))));

        root.then(net.minecraft.commands.Commands.literal("registry")
                .requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
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

    private static int grant(CommandContext<CommandSourceStack> ctx, ServerPlayer target, String concept, int level) {
        if (!isPlausibleConcept(concept)) {
            ctx.getSource().sendFailure(Component.translatable("adaptionwheel.cmd.unknown_concept", concept));
            return 0;
        }
        AdaptionEvents.debugGrant(target, concept, level);
        String state = Concepts.isOneTime(concept) ? "adapted" : "Lv." + Math.min(level, PlayerAdaption.MAX_LEVEL);
        ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.grant_done",
                concept, state, target.getName()), true);
        return 1;
    }

    /** Accepts registered definitions plus dynamic per-entity/per-effect key patterns. */
    private static boolean isPlausibleConcept(String concept) {
        if (AdaptationRegistry.isRegistered(concept)) {
            return true;
        }
        return concept.startsWith("Contact_") || concept.startsWith("Offense_NPC_")
                || concept.startsWith("Drop_NPC_") || concept.startsWith("Debuff_")
                || concept.startsWith("Existence_") || concept.startsWith("Type_")
                || concept.startsWith("Mine_") || concept.startsWith("Combat_")
                || concept.startsWith("Percep_") || concept.startsWith("Phys_")
                || concept.equals(Concepts.SELF_DAMAGE) || concept.equals(Concepts.ADVERSITY);
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
