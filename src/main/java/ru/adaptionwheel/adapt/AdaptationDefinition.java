package ru.adaptionwheel.adapt;

import net.minecraft.network.chat.Component;
import ru.adaptionwheel.category.Concepts;

public record AdaptationDefinition(String concept, AdaptationDomain domain, boolean leveled, int maxLevel) {

    public static final int DEFAULT_MAX_LEVEL = ru.adaptionwheel.data.PlayerAdaption.MAX_LEVEL;

    public static AdaptationDefinition oneTime(String concept, AdaptationDomain domain) {
        return new AdaptationDefinition(concept, domain, false, 1);
    }

    public static AdaptationDefinition leveled(String concept, AdaptationDomain domain) {
        return new AdaptationDefinition(concept, domain, true, DEFAULT_MAX_LEVEL);
    }

    public Component displayName() {
        return Concepts.displayName(concept);
    }

    public String descriptionKey() {
        return "adaptionwheel.desc." + concept;
    }
}
