package ru.adaptionwheel.adapt;

import net.minecraft.network.chat.Component;

public enum AdaptationDomain {

    DAMAGE("damage", 0xFFFFA500),

    EFFECT("effect", 0xFFFFFF00),

    MOVEMENT("movement", 0xFF90EE90),

    PHYSICS("physics", 0xFF6CA6E0),

    MINING("mining", 0xFFB0B0B0),

    ENVIRONMENT("environment", 0xFF00FFFF),

    COMBAT("combat", 0xFFFF6B6B),

    PERCEPTION("perception", 0xFFE0B0FF),

    ENTITY("entity", 0xFFDDA0DD),

    EXISTENCE("existence", 0xFFFFFFFF),

    SPECIAL("special", 0xFF9370DB);

    private final String key;
    private final int color;

    AdaptationDomain(String key, int color) {
        this.key = key;
        this.color = color;
    }

    public String getKey() {
        return key;
    }

    public Component translation() {
        return Component.translatable("adaptionwheel.domain." + key);
    }

    public int getColor() {
        return color;
    }
}
