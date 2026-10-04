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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;
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

@EventBusSubscriber(modid = AdaptionWheel.MODID)
public final class AdaptionCommand {

    private AdaptionCommand() {
    }

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

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> DOMAIN_SUGGESTIONS =
            (ctx, builder) -> {
                for (AdaptationDomain domain : AdaptationDomain.values()) {
                    builder.suggest(domain.getKey());
                }
                return builder.buildFuture();
            };

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String>
            conceptArg(String name) {
        return net.minecraft.commands.Commands.argument(name, StringArgumentType.string())
                .suggests(CONCEPT_SUGGESTIONS);
    }

    private static final com.mojang.brigadier.arguments.IntegerArgumentType LEVEL_ARG =
            IntegerArgumentType.integer(0, PlayerAdaption.MAX_LEVEL);

    private static final int LEVEL_DEFAULT = -1;

    private static int level(CommandContext<CommandSourceStack> ctx) {
        return IntegerArgumentType.getInteger(ctx, "level");
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = net.minecraft.commands.Commands
                .literal("adaptionwheel");

        root.executes(ctx -> {
            ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.usage"), false);
            ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.usage_hint"), false);
            return 1;
        });

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
                                target = null;
                            }
                            return info(ctx, target, StringArgumentType.getString(ctx, "concept"));
                        })));

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

                        .executes(ctx -> grant(ctx, selfOrTarget(ctx, "target"), concept(ctx), LEVEL_DEFAULT))

                        .then(net.minecraft.commands.Commands.argument("target", EntityArgument.player())
                                .requires(s -> s.hasPermission(2))
                                .executes(ctx -> grant(ctx, EntityArgument.getPlayer(ctx, "target"),
                                        concept(ctx), LEVEL_DEFAULT)))
                        .then(net.minecraft.commands.Commands.argument("level", LEVEL_ARG)
                                .executes(ctx -> grant(ctx, self(ctx), concept(ctx), level(ctx)))
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

        root.then(net.minecraft.commands.Commands.literal("shed")
                .then(conceptArg("concept")
                        .executes(ctx -> shed(ctx, selfOrTarget(ctx, "target"), concept(ctx)))
                        .then(net.minecraft.commands.Commands.argument("target", EntityArgument.player())
                                .requires(s -> s.hasPermission(2))
                                .executes(ctx -> shed(ctx, EntityArgument.getPlayer(ctx, "target"),
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

        root.then(net.minecraft.commands.Commands.literal("debug")
                .then(net.minecraft.commands.Commands.literal("aggro")

                        .requires(s2 -> s2.hasPermission(2))
                        .executes(ctx -> aggro(ctx, self(ctx))))
                .then(net.minecraft.commands.Commands.literal("altar")
                        .requires(s2 -> s2.hasPermission(2))
                        .executes(ctx -> altarLookup(ctx, null))
                        .then(net.minecraft.commands.Commands.argument("item",
                                        StringArgumentType.word())
                                .executes(ctx -> altarLookup(ctx,
                                        StringArgumentType.getString(ctx, "item")))))
                .then(net.minecraft.commands.Commands.literal("flight")
                        .requires(s2 -> s2.hasPermission(2))
                        .executes(ctx -> flight(ctx, self(ctx)))
                        .then(net.minecraft.commands.Commands.argument("player",
                                        EntityArgument.player())
                                .executes(ctx -> flight(ctx, selfOrTarget(ctx, "player"))))));

        event.getDispatcher().register(root);
    }

    private static ServerPlayer self(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    private static ServerPlayer selfOrTarget(CommandContext<CommandSourceStack> ctx, String name)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try {
            return EntityArgument.getPlayer(ctx, name);
        } catch (IllegalArgumentException ignored) {

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
            reportUnknownConcept(ctx, concept);
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
        Component truncated = truncationHint(concept);
        if (truncated != null) {
            ctx.getSource().sendFailure(truncated);
            return 0;
        }
        Shedding.Refusal refusal = Shedding.shed(target, AdaptionEvents.dataOf(target), concept);
        if (refusal != Shedding.Refusal.OK) {
            ctx.getSource().sendFailure(Component.translatable(
                    "adaptionwheel.cmd.shed_" + refusal.name().toLowerCase(java.util.Locale.ROOT),
                    concept));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.shed_done",
                concept, target.getName()), true);
        return 1;
    }

    private static int ungrant(CommandContext<CommandSourceStack> ctx, ServerPlayer target,
                               String concept) {
        Component truncated = truncationHint(concept);
        if (truncated != null) {
            ctx.getSource().sendFailure(truncated);
            return 0;
        }
        if (!AdaptionEvents.debugUngrant(target, concept)) {
            ctx.getSource().sendFailure(Component.translatable("adaptionwheel.cmd.ungrant_absent",
                    concept, target.getName()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.ungrant_done",
                concept, target.getName()), true);
        return 1;
    }

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

    private static java.util.Set<String> knownNamespaces() {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        out.add("minecraft");
        try {
            for (var mod : net.neoforged.fml.ModList.get().getMods()) {
                out.add(mod.getModId());
            }
        } catch (Throwable ignored) {

        }
        return out;
    }

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

        List<net.minecraft.world.entity.Mob> hostile = level.getEntitiesOfClass(
                net.minecraft.world.entity.Mob.class, target.getBoundingBox().inflate(24.0D),
                mob -> mob instanceof net.minecraft.world.entity.monster.Enemy);
        source.sendSuccess(() -> Component.literal("hostile mobs within 24 blocks: " + hostile.size()), false);
        int shown = 0;
        for (net.minecraft.world.entity.Mob mob : hostile) {
            if (shown++ >= 5) {
                source.sendSuccess(() -> Component.literal("... and " + (hostile.size() - 5) + " more"), false);
                break;
            }
            var mobTarget = mob.getTarget();
            source.sendSuccess(() -> Component.literal("  " + mob.getName().getString() + " -> "
                    + (mobTarget == null ? "(nothing)"
                    : mobTarget.getName().getString() + " at "
                    + String.format(java.util.Locale.ROOT, "%.1f", mob.distanceTo(target)) + " blocks")), false);
        }
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

        ru.adaptionwheel.data.WheelData onItem = AdaptionEvents.getWheelStack(target)
                .map(s -> s.get(ru.adaptionwheel.data.ModDataComponents.WHEEL_DATA))
                .orElse(null);
        int itemCount = onItem == null ? -1 : onItem.adaptCount();
        source.sendSuccess(() -> Component.literal("attachment adaptCount=" + data.getAdaptCount()
                + "   wheel item wheel_data adaptCount=" + (itemCount < 0 ? "(no component)" : itemCount)), false);
        if (wearing && itemCount >= 0 && itemCount != data.getAdaptCount()) {
            source.sendSuccess(() -> Component.literal(">>> THESE DIFFER. Equipping REPLACES the attachment with the "
                    + "wheel item's data, so anything granted while the wheel was off is lost."), false);
        }
        if (onItem != null && itemCount > 0
                && !onItem.adapted().contains(concept) && data.isAdapted(concept)) {
            source.sendSuccess(() -> Component.literal(">>> " + Concepts.chatName(concept)
                    + " is in the attachment but NOT on the wheel item: re-equipping will wipe it."), false);
        }

        double altitude = target.getY();
        double needed = AdaptionConfig.FLIGHT_ALTITUDE.get();
        int phantom = data.level(Concepts.contact("minecraft:phantom"));
        boolean levitation = data.isAdapted(Concepts.debuff("minecraft:levitation"));
        source.sendSuccess(() -> Component.literal("unlock: y=" + String.format(java.util.Locale.ROOT, "%.1f", altitude)
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
        } else {
            source.sendSuccess(() -> Component.literal("=> mayfly is SET. Flight is a DOUBLE TAP of jump while not "
                    + "standing still; vanilla only arms it within 7 ticks of the first tap."), false);
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
                    + " mobs have loot here. /adaptionwheel debug altar <item> for one item."), false);
            return mobs;
        }
        var id = net.minecraft.resources.ResourceLocation.tryParse(itemId);
        if (id == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)) {
            source.sendSuccess(() -> Component.literal("no such item: " + itemId), false);
            return 0;
        }
        var stack = new net.minecraft.world.item.ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id));
        List<String> mobs = AltarOfferings.mobsFor(stack, server);
        source.sendSuccess(() -> Component.literal(itemId + " opens: "
                + (mobs.isEmpty() ? "(nothing - not an offering)"
                : String.join(", ", mobs))), false);
        ru.adaptionwheel.server.DomainExchange.Recipe recipe =
                ru.adaptionwheel.server.DomainExchange.recipeFor(stack);
        if (recipe != null) {
            source.sendSuccess(() -> Component.literal("  price list: " + recipe.itemsPerTrade()
                    + " item(s) for the first level, selectors "
                    + recipe.selectors().stream()
                            .map(ru.adaptionwheel.server.DomainExchange.Selector::text)
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
}
