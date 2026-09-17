package ru.adaptionwheel.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the attack-charge ticker for {@code Combat_Cooldown}.
 * The field lives in {@code LivingEntity}, and Mixin's @Shadow only resolves
 * members declared in the exact target class — hence this accessor.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityTickerAccessor {

    @Accessor("attackStrengthTicker")
    int adaptionwheel$getAttackStrengthTicker();

    @Accessor("attackStrengthTicker")
    void adaptionwheel$setAttackStrengthTicker(int value);
}
