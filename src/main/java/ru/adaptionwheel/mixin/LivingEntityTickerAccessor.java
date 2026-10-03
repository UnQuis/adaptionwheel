package ru.adaptionwheel.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityTickerAccessor {

    @Accessor("attackStrengthTicker")
    int adaptionwheel$getAttackStrengthTicker();

    @Accessor("attackStrengthTicker")
    void adaptionwheel$setAttackStrengthTicker(int value);
}
