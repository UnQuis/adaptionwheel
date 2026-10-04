package ru.adaptionwheel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.material.FogType;
import ru.adaptionwheel.category.Concepts;

public final class SeaEyeFog {

    private SeaEyeFog() {
    }

    public static FogType effective(FogType reported) {
        if (reported == FogType.NONE || reported == FogType.POWDER_SNOW || !seaEye()) {
            return reported;
        }
        return FogType.NONE;
    }

    public static boolean seaEye() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && ClientAdaption.ADAPTED.contains(Concepts.MUTATION_SEA_EYE);
    }
}