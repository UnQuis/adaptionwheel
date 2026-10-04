package ru.adaptionwheel.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record Extras(List<String> disabled, List<net.minecraft.world.item.ItemStack> cache) {

    public static final Extras EMPTY = new Extras(List.of(), List.of());

    public static final Codec<Extras> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("disabled", List.of()).forGetter(Extras::disabled),
            net.minecraft.world.item.ItemStack.CODEC.listOf().optionalFieldOf("cache", List.of())
                    .forGetter(Extras::cache)
    ).apply(instance, Extras::new));
}

