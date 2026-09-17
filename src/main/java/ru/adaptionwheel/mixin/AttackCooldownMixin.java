package ru.adaptionwheel.mixin;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.adaptionwheel.SurfaceAdaptations;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.config.AdaptionConfig;

/**
 * Combat_Cooldown — 1.7-style attack recovery.
 *
 * <p>Instead of patching the damage formula, the charge itself recovers faster:
 * right after vanilla's natural {@code attackStrengthTicker++} in
 * {@link Player#tick()}, adapted wearers close {@code cooldownRecoveryPct} of
 * the remaining gap to the weapon's full delay. At MAX the charge is restored
 * within one tick of any hit — no cooldown at all, exactly like pre-1.9 combat,
 * and the crosshair charge indicator shows it.</p>
 *
 * <p>Running inside {@code tick()} on BOTH sides keeps the visible indicator,
 * crit gating and damage math perfectly consistent without touching
 * {@code Player.attack}. The ticker field is declared in {@code LivingEntity},
 * so it is reached through {@link LivingEntityTickerAccessor}.</p>
 */
@Mixin(Player.class)
public abstract class AttackCooldownMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void adaptionwheel$fastRecovery(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        LivingEntityTickerAccessor accessor = (LivingEntityTickerAccessor) self;
        int fullChargeTicks = Mth.ceil(self.getCurrentItemAttackStrengthDelay());
        int ticker = accessor.adaptionwheel$getAttackStrengthTicker();
        if (ticker >= fullChargeTicks) {
            return;
        }
        int level = SurfaceAdaptations.conceptLevel(self, Concepts.COMBAT_COOLDOWN);
        if (level <= 0) {
            return;
        }
        double recoveryPct = AdaptionConfig.cooldownRecovery(level);
        int missing = fullChargeTicks - ticker;
        int add = (int) Math.ceil(missing * recoveryPct / 100.0);
        if (add > 0) {
            accessor.adaptionwheel$setAttackStrengthTicker(Math.min(fullChargeTicks, ticker + add));
        }
    }
}
