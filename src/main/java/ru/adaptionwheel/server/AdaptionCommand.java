package ru.adaptionwheel.server;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.data.ModDataComponents;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.data.WheelData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The /adaptionwheel command tree.
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
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID)
public final class AdaptionCommand {

    private AdaptionCommand() {
    }

    private static final SuggestionProvider<CommandSourceStack> CONCEPT_SUGGESTIONS =
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

    private static final SuggestionProvider<CommandSourceStack> DOMAIN_SUGGESTIONS =
            (ctx, builder) -> {
                for (AdaptationDomain domain : AdaptationDomain.values()) {
                    builder.suggest(domain.getKey());
                }
                return builder.buildFuture();
            };

    private static RequiredArgumentBuilder<CommandSourceStack, String> conceptArg(String name) {
        return Commands.argument(name, StringArgumentType.string())
                .suggests(CONCEPT_SUGGESTIONS);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> domainArg(String name) {
        return Commands.argument(name, StringArgumentType.word())
                .suggests(DOMAIN_SUGGESTIONS);
    }

    private static final IntegerArgumentType LEVEL_ARG =
            IntegerArgumentType.integer(0, PlayerAdaption.MAX_LEVEL);

    private static final int LEVEL_DEFAULT = -1;

    private static int level(CommandContext<CommandSourceStack> ctx) {
        return IntegerArgumentType.getInteger(ctx, "level");
    }

    private static String domain(CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "domain");
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("adaptionwheel");

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

        root.then(Commands.literal("info")
                .then(conceptArg("concept")
                        .executes(ctx -> {
                            ServerPlayer target;
                            try {
                                target = self(ctx);
                            } catch (CommandSyntaxException e) {
                                target = null;
                            }
                            return info(ctx, target, concept(ctx));
                        })));

        // grant <concept>                       -> self, default level
        // grant <concept> <target>               -> target, default level
        // grant <concept> level <level>          -> self, explicit level
        // grant <concept> level <level> <target> -> target, explicit level
        //
        // "level" is a literal keyword, not a second argument sibling of "target": a player can
        // legally be named anything an integer can also look like, so a bare integer argument and
        // a bare player argument must never compete for the same token. See the class javadoc.
        root.then(Commands.literal("grant")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("all")
                        .executes(ctx -> grantAll(ctx, self(ctx), null))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> grantAll(ctx, EntityArgument.getPlayer(ctx, "target"), null)))
                        .then(Commands.literal("domain")
                                .then(domainArg("domain")
                                        .executes(ctx -> grantAll(ctx, self(ctx), domain(ctx)))
                                        .then(Commands.argument("target", EntityArgument.player())
                                                .executes(ctx -> grantAll(ctx,
                                                        EntityArgument.getPlayer(ctx, "target"), domain(ctx)))))))
                .then(conceptArg("concept")
                        .executes(ctx -> grant(ctx, self(ctx), concept(ctx), LEVEL_DEFAULT))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> grant(ctx, EntityArgument.getPlayer(ctx, "target"),
                                        concept(ctx), LEVEL_DEFAULT)))
                        .then(Commands.literal("level")
                                .then(Commands.argument("level", LEVEL_ARG)
                                        .executes(ctx -> grant(ctx, self(ctx), concept(ctx), level(ctx)))
                                        .then(Commands.argument("target", EntityArgument.player())
                                                .executes(ctx -> grant(ctx, EntityArgument.getPlayer(ctx, "target"),
                                                        concept(ctx), level(ctx))))))));

        root.then(Commands.literal("ungrant")
                .requires(s -> s.hasPermission(2))
                .then(conceptArg("concept")
                        .executes(ctx -> ungrant(ctx, selfOrTarget(ctx, "target"), concept(ctx)))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> ungrant(ctx, EntityArgument.getPlayer(ctx, "target"),
                                        concept(ctx))))));

        root.then(Commands.literal("shed")
                .then(conceptArg("concept")
                        .executes(ctx -> shed(ctx, selfOrTarget(ctx, "target"), concept(ctx)))
                        .then(Commands.argument("target", EntityArgument.player())
                                .requires(s -> s.hasPermission(2))
                                .executes(ctx -> shed(ctx, EntityArgument.getPlayer(ctx, "target"),
                                        concept(ctx))))));

        root.then(Commands.literal("analyze")
                .requires(s -> s.hasPermission(2))
                .then(conceptArg("concept")
                        .executes(ctx -> analyze(ctx, selfOrTarget(ctx, "target"), concept(ctx)))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> analyze(ctx, EntityArgument.getPlayer(ctx, "target"),
                                        concept(ctx))))));

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
                        .then(Commands.argument("item", StringArgumentType.word())
                                .executes(ctx -> altarLookup(ctx,
                                        StringArgumentType.getString(ctx, "item")))))
                .then(Commands.literal("flight")
                        .executes(ctx -> flight(ctx, self(ctx)))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> flight(ctx, selfOrTarget(ctx, "player"))))));

        event.getDispatcher().register(root);
    }

    private static ServerPlayer self(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
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

    private static final SimpleCommandExceptionType NO_PLAYER_ERROR =
            new SimpleCommandExceptionType(Component.translatable("adaptionwheel.cmd.no_player"));

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

    private static List<String> nearMatches(String needle) {
        String lower = needle.toLowerCase(Locale.ROOT);
        List<String> hits = new ArrayList<>();
        for (AdaptationDefinition def : AdaptationRegistry.allDefinitions()) {
            if (def.concept().toLowerCase(Locale.ROOT).contains(lower)) {
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
                    + " mobs have loot here. /adaptionwheel debug altar <item> for one item."), false);
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
}