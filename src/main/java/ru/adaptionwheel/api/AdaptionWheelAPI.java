package ru.adaptionwheel.api;

import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import ru.adaptionwheel.adapt.AdaptationDefinition;
import ru.adaptionwheel.adapt.AdaptationDomain;
import ru.adaptionwheel.adapt.AdaptationRegistry;
import ru.adaptionwheel.category.AdaptionCategory;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.server.AdaptionEvents;

/**
 * Public entry point for other mods interacting with the Adaption Wheel.
 *
 * <p>All player-facing queries are server-side authoritative: on the client
 * they return "not wearing"/0. Use {@link ru.adaptionwheel.api.events.AdaptationCompleteEvent}
 * to observe adaptation completion.</p>
 */
public final class AdaptionWheelAPI {

    private AdaptionWheelAPI() {
    }

    /** True when the player currently wears the Mahoraga Wheel in its Curios slot (server side). */
    public static boolean isWearingWheel(Player player) {
        return !player.level().isClientSide && AdaptionEvents.isWearingWheel(player);
    }

    /** Level of a leveled adaptation concept ({@code Type_*}, {@code Contact_*}, ...), 0 if absent. */
    public static int getLevel(Player player, String concept) {
        if (player.level().isClientSide || !(player instanceof net.minecraft.server.level.ServerPlayer sp)) {
            return 0;
        }
        return AdaptionEvents.dataOf(sp).level(concept);
    }

    /** Whether a one-time adaptation (Env_*, Debuff_*, Existence_*, Mutation_*, ...) is completed. */
    public static boolean isAdapted(Player player, String concept) {
        if (player.level().isClientSide || !(player instanceof net.minecraft.server.level.ServerPlayer sp)) {
            return false;
        }
        return AdaptionEvents.dataOf(sp).isAdapted(concept);
    }

    /** Total number of completed adaptations (drives cumulative bonuses). */
    public static int getAdaptCount(Player player) {
        if (player.level().isClientSide || !(player instanceof net.minecraft.server.level.ServerPlayer sp)) {
            return 0;
        }
        return AdaptionEvents.dataOf(sp).getAdaptCount();
    }

    /** Concepts of all running analysis tasks. */
    public static List<String> getActiveTasks(Player player) {
        if (player.level().isClientSide || !(player instanceof net.minecraft.server.level.ServerPlayer sp)) {
            return List.of();
        }
        return AdaptionEvents.dataOf(sp).tasks.stream().map(t -> t.concept).toList();
    }

    /** Builds a standard concept key for a damage category, e.g. {@code type(FIRE) == "Type_FIRE"}. */
    public static String typeConcept(AdaptionCategory category) {
        return Concepts.type(category);
    }

    /** Builds a standard per-entity contact/offense/drop/existence concept key from an entity id path. */
    public static String entityConcept(String prefix, String entityIdPath) {
        return switch (prefix.toLowerCase(Locale.ROOT)) {
            case "contact" -> Concepts.contact(entityIdPath);
            case "offense" -> Concepts.offense(entityIdPath);
            case "drop" -> Concepts.drop(entityIdPath);
            case "existence" -> Concepts.existence(entityIdPath);
            default -> prefix + "_" + entityIdPath;
        };
    }

    /** Registers GUI/HUD metadata for a custom concept (safe to call any time before use). */
    public static void registerDefinition(AdaptationDefinition definition) {
        AdaptationRegistry.register(definition);
    }

    @Nullable
    public static AdaptationDefinition getDefinition(String concept) {
        return AdaptationRegistry.get(concept);
    }

    /** Organizational domain of any concept key (works for unregistered dynamic keys too). */
    public static AdaptationDomain domainOf(String concept) {
        return AdaptationRegistry.domainOf(concept);
    }

    /** Localized display name of any concept key. */
    public static Component displayName(String concept) {
        return Concepts.displayName(concept);
    }
}
