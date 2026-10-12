package ru.adaptionwheel.server;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.adapt.AdaptationDefinition;
import ru.adaptionwheel.adapt.AdaptationDomain;
import ru.adaptionwheel.adapt.AdaptationRegistry;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.data.ModDataComponents;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.server.DomainExchange.Recipe;
import ru.adaptionwheel.server.DomainExchange.Selector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/** One command tree with explicit player targets for every console-capable operation. */
@EventBusSubscriber(modid = AdaptionWheel.MODID)
public final class AdaptionCommand {

    private static final int LEVEL_DEFAULT = -1;
    private static final Predicate<CommandSourceStack> ADMIN =
            Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);

    private static final SimpleCommandExceptionType PLAYER_SOURCE_REQUIRED =
            new SimpleCommandExceptionType(Component.translatable("adaptionwheel.cmd.no_player"));

    private static final String[] DYNAMIC_PREFIXES = {
            "Type_", "Env_", "Debuff_", "Contact_", "Offense_NPC_", "Drop_NPC_", "Existence_",
            "Mutation_", "Move_", "Percep_", "Mine_", "Combat_", "Phys_", "Fist_", "Dimension_",
            "Proj_", "DamageClass_"
    };

    private static final SuggestionProvider<CommandSourceStack> CONCEPT_SUGGESTIONS = (ctx, builder) -> {
        for (AdaptationDefinition definition : AdaptationRegistry.allDefinitions()) {
            suggestConcept(builder, definition.concept());
        }
        for (String prefix : DYNAMIC_PREFIXES) {
            builder.suggest(prefix);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> DOMAIN_SUGGESTIONS = (ctx, builder) -> {
        for (AdaptationDomain domain : AdaptationDomain.values()) {
            builder.suggest(domain.getKey());
        }
        return builder.buildFuture();
    };

    private AdaptionCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(buildRoot());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRoot() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("adaptionwheel")
                .executes(AdaptionCommand::help);

        root.then(Commands.literal("help").executes(AdaptionCommand::help));
        root.then(statusCommand());
        root.then(listCommand());
        root.then(infoCommand());
        root.then(grantCommand("grant"));
        root.then(grantCommand("give"));
        root.then(grantAllCommand("grantall"));
        root.then(grantAllCommand("grant_all"));
        root.then(ungrantCommand("ungrant"));
        root.then(ungrantCommand("revoke"));
        root.then(analyzeCommand());
        root.then(resetCommand());
        root.then(shedCommand());
        root.then(Commands.literal("registry").requires(ADMIN).executes(AdaptionCommand::registry));
        root.then(debugCommand());
        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> statusCommand() {
        return Commands.literal("status")
                .executes(ctx -> status(ctx, requirePlayer(ctx)))
                .then(Commands.argument("player", EntityArgument.player())
                        .requires(ADMIN)
                        .executes(ctx -> status(ctx, target(ctx))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> listCommand() {
        return Commands.literal("list")
                .executes(ctx -> list(ctx, requirePlayer(ctx), null))
                .then(domainArgument().executes(ctx -> list(ctx, requirePlayer(ctx), domain(ctx))))
                .then(Commands.literal("player")
                        .requires(ADMIN)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> list(ctx, target(ctx), null))
                                .then(domainArgument().executes(ctx -> list(ctx, target(ctx), domain(ctx))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> infoCommand() {
        return Commands.literal("info")
                .then(conceptArgument().executes(ctx -> info(ctx, ctx.getSource().getPlayer(), concept(ctx))))
                .then(Commands.literal("player")
                        .requires(ADMIN)
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(conceptArgument().executes(ctx -> info(ctx, target(ctx), concept(ctx))))));
    }

    /**
     * Console form: /adaptionwheel grant <online-player> <concept> [level].
     * Player form is identical, so there is no implicit "self" target to misparse.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> grantCommand(String name) {
        return Commands.literal(name).requires(ADMIN)
                .then(Commands.argument("player", EntityArgument.player())
                        .then(conceptArgument()
                                .executes(ctx -> grant(ctx, target(ctx), concept(ctx), LEVEL_DEFAULT))
                                .then(Commands.argument("level",
                                                IntegerArgumentType.integer(0, PlayerAdaption.MAX_LEVEL))
                                        .executes(ctx -> grant(ctx, target(ctx), concept(ctx),
                                                IntegerArgumentType.getInteger(ctx, "level"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> grantAllCommand(String name) {
        return Commands.literal(name).requires(ADMIN)
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> grantAll(ctx, target(ctx), null))
                        .then(domainArgument().executes(ctx -> grantAll(ctx, target(ctx), domain(ctx)))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> ungrantCommand(String name) {
        return Commands.literal(name).requires(ADMIN)
                .then(Commands.argument("player", EntityArgument.player())
                        .then(conceptArgument().executes(ctx -> ungrant(ctx, target(ctx), concept(ctx)))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> analyzeCommand() {
        return Commands.literal("analyze").requires(ADMIN)
                .then(Commands.argument("player", EntityArgument.player())
                        .then(conceptArgument().executes(ctx -> analyze(ctx, target(ctx), concept(ctx)))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> resetCommand() {
        return Commands.literal("reset").requires(ADMIN)
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> reset(ctx, target(ctx))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> shedCommand() {
        return Commands.literal("shed")
                .then(conceptArgument().executes(ctx -> shed(ctx, requirePlayer(ctx), concept(ctx))))
                .then(Commands.literal("player")
                        .requires(ADMIN)
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(conceptArgument().executes(ctx -> shed(ctx, target(ctx), concept(ctx))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> debugCommand() {
        return Commands.literal("debug").requires(ADMIN)
                .then(Commands.literal("aggro")
                        .executes(ctx -> aggro(ctx, requirePlayer(ctx)))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> aggro(ctx, target(ctx)))))
                .then(Commands.literal("flight")
                        .executes(ctx -> flight(ctx, requirePlayer(ctx)))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> flight(ctx, target(ctx)))))
                .then(Commands.literal("altar")
                        .executes(ctx -> altarLookup(ctx, null))
                        .then(Commands.argument("item", StringArgumentType.greedyString())
                                .executes(ctx -> altarLookup(ctx,
                                        StringArgumentType.getString(ctx, "item")))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> conceptArgument() {
        return Commands.argument("concept", StringArgumentType.string()).suggests(CONCEPT_SUGGESTIONS);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> domainArgument() {
        return Commands.argument("domain", StringArgumentType.word()).suggests(DOMAIN_SUGGESTIONS);
    }

    private static void suggestConcept(com.mojang.brigadier.suggestion.SuggestionsBuilder builder, String value) {
        // StringArgumentType.string() requires namespaced values to be quoted; put valid text in tab completion.
        builder.suggest(value.indexOf(':') >= 0 ? '"' + value + '"' : value);
    }

    private static ServerPlayer requirePlayer(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            throw PLAYER_SOURCE_REQUIRED.create();
        }
        return player;
    }

    private static ServerPlayer target(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return EntityArgument.getPlayer(ctx, "player");
    }

    private static String concept(CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "concept");
    }

    private static String domain(CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "domain");
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.usage"), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.usage_extra"), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.usage_hint"), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        PlayerAdaption data = AdaptionEvents.dataOf(player);
        ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.status",
                player.getName(), data.getAdaptCount(), data.adversityActive ? "ACTIVE" : "idle",
                data.adversityCooldownTimer / 20, data.tasks.size()), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx, ServerPlayer player,
                            @Nullable String domainName) {
        AdaptationDomain filter = null;
        if (domainName != null) {
            filter = parseDomain(domainName);
            if (filter == null) {
                ctx.getSource().sendFailure(Component.translatable("adaptionwheel.cmd.unknown_domain", domainName));
                return 0;
            }
        }

        PlayerAdaption data = AdaptionEvents.dataOf(player);
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(data.adapted);
        keys.addAll(data.levels.keySet());
        List<String> sorted = new ArrayList<>();
        for (String key : keys) {
            if (filter == null || AdaptationRegistry.domainOf(key) == filter) {
                sorted.add(key);
            }
        }
        sorted.sort(Comparator
                .comparingInt((String key) -> AdaptationRegistry.domainOf(key).ordinal())
                .thenComparing(Comparator.naturalOrder()));

        MutableComponent header = Component.translatable("adaptionwheel.cmd.list_header", player.getName(), sorted.size());
        if (filter != null) {
            header.append(Component.literal(" [" + filter.getKey() + "]"));
        }
        ctx.getSource().sendSuccess(() -> header, false);

        for (String key : sorted) {
            AdaptationDefinition definition = AdaptationRegistry.get(key);
            boolean oneTime = definition != null ? !definition.leveled() : Concepts.isOneTime(key);
            int currentLevel = data.level(key);
            String state = oneTime
                    ? (data.isAdapted(key) ? "ADAPTED" : "-")
                    : (currentLevel >= PlayerAdaption.MAX_LEVEL ? "MAX" : "Lv." + currentLevel);
            if (!data.isEnabled(key)) {
                state += " [OFF]";
            }
            final String displayState = state;
            ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.list_entry",
                    Concepts.displayName(key), AdaptationRegistry.domainOf(key).getKey(), displayState), false);
        }

        if (sorted.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.list_empty"), false);
            return 0;
        }
        return sorted.size();
    }

    private static AdaptationDomain parseDomain(String raw) {
        for (AdaptationDomain domain : AdaptationDomain.values()) {
            if (domain.getKey().equalsIgnoreCase(raw)) {
                return domain;
            }
        }
        return null;
    }

    private static int info(CommandContext<CommandSourceStack> ctx, @Nullable ServerPlayer player, String key) {
        if (rejectTruncatedConcept(ctx, key)) {
            return 0;
        }
        if (!isPlausibleConcept(key)) {
            reportUnknownConcept(ctx, key);
            return 0;
        }
        AdaptationDefinition definition = AdaptationRegistry.get(key);
        boolean oneTime = definition != null ? !definition.leveled() : Concepts.isOneTime(key);
        int maxLevel = definition != null ? definition.maxLevel() : PlayerAdaption.MAX_LEVEL;
        int level = player == null ? 0 : AdaptionEvents.dataOf(player).level(key);
        String state;
        if (player == null) {
            state = oneTime ? "one-time" : "leveled";
        } else if (oneTime) {
            state = AdaptionEvents.dataOf(player).isAdapted(key) ? "ADAPTED" : "not adapted";
        } else {
            state = level >= maxLevel ? "MAX" : "Lv." + level;
        }
        if (player != null && !AdaptionEvents.dataOf(player).isEnabled(key)) {
            state += " [OFF]";
        }
        final String displayState = state;

        ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.info",
                Concepts.displayName(key), key,
                oneTime ? Component.literal("-") : Component.literal(player == null ? "-" : String.valueOf(level)),
                oneTime ? Component.literal("-") : Component.literal(String.valueOf(maxLevel)),
                displayState), false);
        return 1;
    }

    private static int grant(CommandContext<CommandSourceStack> ctx, ServerPlayer player,
                             String key, int requestedLevel) {
        if (rejectTruncatedConcept(ctx, key)) {
            return 0;
        }
        if (!isPlausibleConcept(key)) {
            reportUnknownConcept(ctx, key);
            return 0;
        }

        boolean oneTime = Concepts.isOneTime(key);
        if (oneTime && requestedLevel != LEVEL_DEFAULT) {
            ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.grant_level_ignored", key), false);
        }
        int grantedLevel = oneTime ? PlayerAdaption.MAX_LEVEL
                : requestedLevel == LEVEL_DEFAULT ? PlayerAdaption.MAX_LEVEL : requestedLevel;
        AdaptionEvents.debugGrant(player, key, grantedLevel);
        String state = oneTime ? "ADAPTED" : "Lv." + grantedLevel;
        ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.grant_done",
                key, state, player.getName()), true);
        return 1;
    }

    private static int grantAll(CommandContext<CommandSourceStack> ctx, ServerPlayer player,
                                @Nullable String domainName) {
        AdaptationDomain filter = null;
        if (domainName != null) {
            filter = parseDomain(domainName);
            if (filter == null) {
                ctx.getSource().sendFailure(Component.translatable("adaptionwheel.cmd.unknown_domain", domainName));
                return 0;
            }
        }

        int granted = 0;
        for (AdaptationDefinition definition : AdaptationRegistry.allDefinitions()) {
            if (filter != null && definition.domain() != filter) {
                continue;
            }
            AdaptionEvents.debugGrant(player, definition.concept(),
                    definition.leveled() ? definition.maxLevel() : PlayerAdaption.MAX_LEVEL);
            granted++;
        }

        final int count = granted;
        final String selectedDomain = filter == null ? "registered" : filter.getKey();
        ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.grant_all_done",
                count, selectedDomain, player.getName()), true);
        return count;
    }

    private static int ungrant(CommandContext<CommandSourceStack> ctx, ServerPlayer player, String key) {
        if (rejectTruncatedConcept(ctx, key)) {
            return 0;
        }
        if (!isPlausibleConcept(key)) {
            reportUnknownConcept(ctx, key);
            return 0;
        }
        if (!AdaptionEvents.debugUngrant(player, key)) {
            ctx.getSource().sendFailure(Component.translatable("adaptionwheel.cmd.ungrant_absent",
                    key, player.getName()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.ungrant_done",
                key, player.getName()), true);
        return 1;
    }

    private static int shed(CommandContext<CommandSourceStack> ctx, ServerPlayer player, String key) {
        if (rejectTruncatedConcept(ctx, key)) {
            return 0;
        }
        if (!isPlausibleConcept(key)) {
            reportUnknownConcept(ctx, key);
            return 0;
        }
        Shedding.Refusal refusal = Shedding.shed(player, AdaptionEvents.dataOf(player), key);
        if (refusal != Shedding.Refusal.OK) {
            ctx.getSource().sendFailure(Component.translatable(
                    "adaptionwheel.cmd.shed_" + refusal.name().toLowerCase(Locale.ROOT), key));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.shed_done",
                key, player.getName()), true);
        return 1;
    }

    private static boolean isPlausibleConcept(String key) {
        if (AdaptationRegistry.isRegistered(key) || key.equals(Concepts.SELF_DAMAGE)
                || key.equals(Concepts.ADVERSITY)) {
            return true;
        }
        for (String prefix : DYNAMIC_PREFIXES) {
            if (key.startsWith(prefix) && key.length() > prefix.length()) {
                return true;
            }
        }
        return false;
    }

    private static void reportUnknownConcept(CommandContext<CommandSourceStack> ctx, String key) {
        ctx.getSource().sendFailure(Component.translatable("adaptionwheel.cmd.unknown_concept", key));
        List<String> matches = nearMatches(key);
        if (!matches.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.did_you_mean",
                    String.join(", ", matches)), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.use_registry"), false);
        }
    }

    private static boolean rejectTruncatedConcept(CommandContext<CommandSourceStack> ctx, String concept) {
        Component hint = truncationHint(concept);
        if (hint == null) {
            return false;
        }
        ctx.getSource().sendFailure(hint);
        return true;
    }

    @Nullable
    private static Component truncationHint(String concept) {
        for (String namespace : knownNamespaces()) {
            if (concept.endsWith("_" + namespace)) {
                return Component.translatable("adaptionwheel.cmd.looks_truncated", concept,
                        concept + ":<path>");
            }
        }
        return null;
    }

    private static Set<String> knownNamespaces() {
        Set<String> namespaces = new LinkedHashSet<>();
        namespaces.add("minecraft");
        try {
            for (var mod : ModList.get().getMods()) {
                namespaces.add(mod.getModId());
            }
        } catch (Throwable ignored) {
            // FML may not be available during command-tree tests.
        }
        return namespaces;
    }

    private static List<String> nearMatches(String needle) {
        String lower = needle.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (AdaptationDefinition definition : AdaptationRegistry.allDefinitions()) {
            if (definition.concept().toLowerCase(Locale.ROOT).contains(lower)) {
                matches.add(definition.concept());
                if (matches.size() == 8) {
                    break;
                }
            }
        }
        return matches;
    }

    private static int analyze(CommandContext<CommandSourceStack> ctx, ServerPlayer player, String key) {
        if (rejectTruncatedConcept(ctx, key)) {
            return 0;
        }
        if (!isPlausibleConcept(key)) {
            reportUnknownConcept(ctx, key);
            return 0;
        }
        int ticks = (int) (AdaptionConfig.DEFENSE_ANALYSIS_SECONDS.get() * 20);
        AdaptionEvents.startTask(player, AdaptionEvents.dataOf(player), key, ticks);
        ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.analyze_done",
                key, player.getName()), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        AdaptionEvents.debugReset(player);
        ctx.getSource().sendSuccess(() -> Component.translatable("adaptionwheel.cmd.reset_done",
                player.getName()), true);
        return 1;
    }

    private static int aggro(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = player.level();
        source.sendSuccess(() -> Component.literal("— why mobs do or do not target "
                + player.getName().getString() + " —"), false);
        source.sendSuccess(() -> Component.literal("difficulty: " + level.getDifficulty()
                + "   (peaceful makes every hostile mob passive)"), false);
        source.sendSuccess(() -> Component.literal("gamemode: " + player.gameMode()
                + "   abilities.invulnerable=" + player.getAbilities().invulnerable), false);
        source.sendSuccess(() -> Component.literal("canBeSeenByAnyone: " + player.canBeSeenByAnyone()
                + "   isSpectator=" + player.isSpectator() + "   isAlive=" + player.isAlive()), false);
        source.sendSuccess(() -> Component.literal("canBeSeenAsEnemy: " + player.canBeSeenAsEnemy()
                + "   isInvulnerable=" + player.isInvulnerable()
                + "   isInvisible=" + player.isInvisible()), false);
        if (!player.canBeSeenAsEnemy()) {
            source.sendSuccess(() -> Component.literal("=> NO MOB CAN TARGET THIS PLAYER. "
                    + "Creative, spectator, dead, or explicitly invulnerable."), false);
        }
        String effects = player.getActiveEffects().stream()
                .map(effect -> effect.getEffect().getRegisteredName() + " " + (effect.getAmplifier() + 1))
                .reduce((a, b) -> a + ", " + b).orElse("(none)");
        source.sendSuccess(() -> Component.literal("effects: " + effects), false);

        List<net.minecraft.world.entity.Mob> hostile = level.getEntitiesOfClass(
                net.minecraft.world.entity.Mob.class, player.getBoundingBox().inflate(24.0D),
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
                    + String.format(Locale.ROOT, "%.1f", mob.distanceTo(player)) + " blocks")), false);
        }
        return 1;
    }

    private static int flight(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        CommandSourceStack source = ctx.getSource();
        PlayerAdaption data = AdaptionEvents.dataOf(player);
        boolean wearing = AdaptionEvents.isWearingWheel(player);
        source.sendSuccess(() -> Component.literal("— flight: " + player.getName().getString() + " —"), false);
        source.sendSuccess(() -> Component.literal("wheel worn: " + wearing
                + "   mutations.flight config=" + AdaptionConfig.ENABLE_MUTATION_FLIGHT.get()), false);

        String concept = Concepts.MUTATION_FLIGHT;
        source.sendSuccess(() -> Component.literal(Concepts.chatName(concept)
                + ": adapted=" + data.isAdapted(concept) + " enabled=" + data.isEnabled(concept)), false);

        ru.adaptionwheel.data.WheelData wheelData = AdaptionEvents.getWheelStack(player)
                .map(stack -> stack.get(ModDataComponents.WHEEL_DATA)).orElse(null);
        int itemCount = wheelData == null ? -1 : wheelData.adaptCount();
        source.sendSuccess(() -> Component.literal("attachment adaptCount=" + data.getAdaptCount()
                + "   wheel item wheel_data adaptCount=" + (itemCount < 0 ? "(no component)" : itemCount)), false);
        if (wearing && itemCount >= 0 && itemCount != data.getAdaptCount()) {
            source.sendSuccess(() -> Component.literal(">>> THESE DIFFER. If the item held less than the"
                    + " attachment, equipping it used to throw the difference away."), false);
        }
        if (wheelData != null && itemCount > 0
                && !wheelData.adapted().contains(concept) && data.isAdapted(concept)) {
            source.sendSuccess(() -> Component.literal(">>> " + Concepts.chatName(concept)
                    + " is in the attachment but NOT on the wheel item."), false);
        }

        double altitude = player.getY();
        double needed = AdaptionConfig.FLIGHT_ALTITUDE.get();
        int phantom = data.level(Concepts.contact("minecraft:phantom"));
        boolean levitation = data.isAdapted(Concepts.debuff("minecraft:levitation"));
        source.sendSuccess(() -> Component.literal("unlock: y=" + String.format(Locale.ROOT, "%.1f", altitude)
                + " (needs " + needed + ") " + mark(altitude >= needed)
                + "   " + Concepts.contact("minecraft:phantom") + "=" + phantom + "/"
                + PlayerAdaption.MAX_LEVEL + " " + mark(phantom >= PlayerAdaption.MAX_LEVEL)
                + "   " + Concepts.debuff("minecraft:levitation") + "=" + levitation + " " + mark(levitation)), false);

        source.sendSuccess(() -> Component.literal("gamemode=" + player.gameMode.getGameModeForPlayer()
                + " isCreative=" + player.isCreative() + " isSpectator=" + player.isSpectator()), false);
        source.sendSuccess(() -> Component.literal("mayfly=" + player.getAbilities().mayfly
                + " flying=" + player.getAbilities().flying
                + " flySpeed=" + player.getAbilities().getFlyingSpeed()), false);

        if (!wearing) {
            source.sendSuccess(() -> Component.literal("=> NOT WORN: tickFlight is never reached."), false);
        } else if (!AdaptionConfig.ENABLE_MUTATION_FLIGHT.get()) {
            source.sendSuccess(() -> Component.literal("=> mutations.flight is off in the config."), false);
        } else if (!data.isAdapted(concept)) {
            source.sendSuccess(() -> Component.literal("=> NOT ADAPTED yet: it is granted the first tick you are at y="
                    + needed + " with the other two conditions true."), false);
        } else if (player.isCreative() || player.isSpectator()) {
            source.sendSuccess(() -> Component.literal("=> creative/spectator: tickFlight returns early and never sets "
                    + "mayfly. Creative already flies, so test in SURVIVAL."), false);
        } else if (!data.isEnabled(concept)) {
            source.sendSuccess(() -> Component.literal("=> DISABLED in the adaptation panel, so mayfly is revoked."), false);
        } else if (!player.getAbilities().mayfly) {
            source.sendSuccess(() -> Component.literal("=> adapted, enabled, worn, survival — and mayfly is still false. "
                    + "That is a bug; the two lines above the gate are where to look."), false);
        } else if (!player.getAbilities().flying) {
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

    private static int altarLookup(CommandContext<CommandSourceStack> ctx, @Nullable String itemId) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        if (itemId == null) {
            int mobs = AltarOfferings.allMobs(server).size();
            source.sendSuccess(() -> Component.literal(mobs
                    + " mobs have loot here. /adaptionwheel debug altar <item> for one item."), false);
            return mobs;
        }

        var id = net.minecraft.resources.Identifier.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            source.sendFailure(Component.literal("no such item: " + itemId));
            return 0;
        }
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id)
                .map(holder -> holder.value()).orElse(Items.AIR));
        List<String> mobs = AltarOfferings.mobsFor(stack, server);
        source.sendSuccess(() -> Component.literal(itemId + " opens: "
                + (mobs.isEmpty() ? "(nothing - not an offering)" : String.join(", ", mobs))), false);

        Recipe recipe = DomainExchange.recipeFor(stack);
        if (recipe != null) {
            source.sendSuccess(() -> Component.literal("  price list: " + recipe.itemsPerTrade()
                    + " item(s) for the first level, selectors "
                    + recipe.selectors().stream().map(Selector::text)
                    .reduce((a, b) -> a + ", " + b).orElse("?")), false);
        }
        for (String mob : mobs) {
            source.sendSuccess(() -> Component.literal("  " + Concepts.offense(mob) + ", " + Concepts.drop(mob)), false);
        }
        return Math.max(1, mobs.size());
    }

    private static int registry(CommandContext<CommandSourceStack> ctx) {
        List<AdaptationDefinition> definitions = AdaptationRegistry.allDefinitions();
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "adaptionwheel.cmd.registry_header", definitions.size()), false);
        for (AdaptationDefinition definition : definitions) {
            ctx.getSource().sendSuccess(() -> Component.literal("- " + definition.concept()
                    + " [" + definition.domain().getKey() + "]"
                    + (definition.leveled() ? " Lv1-" + definition.maxLevel() : " one-time"))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(definition.domain().getColor()))), false);
        }
        return definitions.size();
    }
}
