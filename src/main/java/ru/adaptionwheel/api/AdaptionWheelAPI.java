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

public final class AdaptionWheelAPI {

    private AdaptionWheelAPI() {
    }

    public static boolean isWearingWheel(Player player) {
        return !player.level().isClientSide && AdaptionEvents.isWearingWheel(player);
    }

    public static int getLevel(Player player, String concept) {
        if (player.level().isClientSide || !(player instanceof net.minecraft.server.level.ServerPlayer sp)) {
            return 0;
        }
        return AdaptionEvents.dataOf(sp).level(concept);
    }

    public static boolean isAdapted(Player player, String concept) {
        if (player.level().isClientSide || !(player instanceof net.minecraft.server.level.ServerPlayer sp)) {
            return false;
        }
        return AdaptionEvents.dataOf(sp).isAdapted(concept);
    }

    public static int getAdaptCount(Player player) {
        if (player.level().isClientSide || !(player instanceof net.minecraft.server.level.ServerPlayer sp)) {
            return 0;
        }
        return AdaptionEvents.dataOf(sp).getAdaptCount();
    }

    public static List<String> getActiveTasks(Player player) {
        if (player.level().isClientSide || !(player instanceof net.minecraft.server.level.ServerPlayer sp)) {
            return List.of();
        }
        return AdaptionEvents.dataOf(sp).tasks.stream().map(t -> t.concept).toList();
    }

    public static String typeConcept(AdaptionCategory category) {
        return Concepts.type(category);
    }

    public static String entityConcept(String prefix, String entityIdPath) {
        return switch (prefix.toLowerCase(Locale.ROOT)) {
            case "contact" -> Concepts.contact(entityIdPath);
            case "offense" -> Concepts.offense(entityIdPath);
            case "drop" -> Concepts.drop(entityIdPath);
            case "existence" -> Concepts.existence(entityIdPath);
            default -> prefix + "_" + entityIdPath;
        };
    }

    public static void registerDefinition(AdaptationDefinition definition) {
        AdaptationRegistry.register(definition);
    }

    @Nullable
    public static AdaptationDefinition getDefinition(String concept) {
        return AdaptationRegistry.get(concept);
    }

    public static AdaptationDomain domainOf(String concept) {
        return AdaptationRegistry.domainOf(concept);
    }

    public static Component displayName(String concept) {
        return Concepts.displayName(concept);
    }
}
