package ru.adaptionwheel.client;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.category.Concepts;

@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class ClientSurfaceHandler {

    private ClientSurfaceHandler() {
    }

    @SubscribeEvent
    public static void onTickPost(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.isLocalPlayer() || !ClientAdaption.wearingWheel || player.isDeadOrDying()) {
            return;
        }
        if (!player.isInLava() || !ClientAdaption.isAdapted(Concepts.ENV_LAVA)) {
            return;
        }
        applyLavaSwim(player, inputDirection(player));
    }

    private static void applyLavaSwim(Player player, Vec3 inputDir) {

        double waterEff = player.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY)
                * (player.onGround() ? 1.0 : 0.5);
        double f4 = player.isSprinting() ? 0.9 : 0.8;
        double waterDecay = f4 + (0.54600006F - f4) * waterEff;
        double waterAccel = (0.02 + (player.getSpeed() - 0.02) * waterEff)
                * player.getAttributeValue(NeoForgeMod.SWIM_SPEED);

        double decayRatio = waterDecay / 0.5;
        double boost = Math.max(0.0, (waterAccel - 0.02) * waterDecay);
        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(
                v.x * decayRatio + inputDir.x * boost,
                v.y + player.getGravity() * 3.0 / 16.0,
                v.z * decayRatio + inputDir.z * boost);
    }

    private static Vec3 inputDirection(Player player) {
        Vec3 input = new Vec3(player.xxa, 0, player.zza);
        double lenSq = input.lengthSqr();
        if (lenSq < 1.0E-7) {
            return Vec3.ZERO;
        }
        Vec3 dir = lenSq > 1.0 ? input.normalize() : input;
        float rad = player.getYRot() * ((float) Math.PI / 180F);
        float s = Mth.sin(rad);
        float c = Mth.cos(rad);
        return new Vec3(dir.x * c - dir.z * s, 0, dir.z * c + dir.x * s);
    }
}
