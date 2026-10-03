package ru.adaptionwheel.data;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.adaptionwheel.AdaptionWheel;

public class ModDataComponents {

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(BuiltInRegistries.DATA_COMPONENT_TYPE, AdaptionWheel.MODID);

    @SuppressWarnings("unchecked")
    public static final DeferredHolder<DataComponentType<WheelData>, DataComponentType<WheelData>> WHEEL_DATA =
            (DeferredHolder<DataComponentType<WheelData>, DataComponentType<WheelData>>)
            (DeferredHolder<?, ?>) COMPONENTS.register("wheel_data", () ->
                    DataComponentType.<WheelData>builder()
                            .persistent(WheelData.CODEC)
                            .build());

    @SuppressWarnings("unchecked")
    public static final DeferredHolder<DataComponentType<Boolean>, DataComponentType<Boolean>> SWORD_MODE =
            (DeferredHolder<DataComponentType<Boolean>, DataComponentType<Boolean>>)
            (DeferredHolder<?, ?>) COMPONENTS.register("sword_mode", () ->
                    DataComponentType.<Boolean>builder()
                            .persistent(Codec.BOOL)
                            .networkSynchronized(ByteBufCodecs.BOOL)
                            .build());

    private ModDataComponents() {
    }
}
