package ru.adaptionwheel.adapt;

import net.minecraft.network.chat.Component;
import ru.adaptionwheel.category.Concepts;

/**
 * Static metadata for a registered adaptation concept. Purely descriptive —
 * levels/flags themselves live in {@link ru.adaptionwheel.data.PlayerAdaption}
 * exactly as before, so registering or omitting a definition never breaks saves.
 *
 * @param concept   the concept key (e.g. "Move_SoulSand", "Type_FIRE", "Contact_minecraft:zombie")
 * @param domain    organizational domain used by GUI/HUD/commands
 * @param leveled   true when the concept advances through {@code maxLevel} levels,
 *                  false for one-time adaptations
 * @param maxLevel  highest reachable level ({@code 1} when not leveled)
 */
public record AdaptationDefinition(String concept, AdaptationDomain domain, boolean leveled, int maxLevel) {

    public static final int DEFAULT_MAX_LEVEL = ru.adaptionwheel.data.PlayerAdaption.MAX_LEVEL;

    public static AdaptationDefinition oneTime(String concept, AdaptationDomain domain) {
        return new AdaptationDefinition(concept, domain, false, 1);
    }

    public static AdaptationDefinition leveled(String concept, AdaptationDomain domain) {
        return new AdaptationDefinition(concept, domain, true, DEFAULT_MAX_LEVEL);
    }

    /** Localized display name (delegates to {@link Concepts#displayName}). */
    public Component displayName() {
        return Concepts.displayName(concept);
    }

    /** Translation key of the description line; may be absent from lang files. */
    public String descriptionKey() {
        return "adaptionwheel.desc." + concept;
    }
}
