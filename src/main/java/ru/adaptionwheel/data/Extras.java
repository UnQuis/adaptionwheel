package ru.adaptionwheel.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;

/**
 * The overflow fields of {@code PlayerAdaption}, nested because {@code RecordCodecBuilder.group}
 * takes at most 16 components and that record was already at the limit.
 *
 * <p>{@code progress} is here because a counter is progress, and losing it is the bug this field
 * exists to fix: the breaking fist's blocks-toward-next-level and the punching fist's
 * kills-toward-next-stage both lived in static UUID-keyed maps that {@code forget()} cleared on
 * logout, so "Netherite fist 20/166" became "0/166" on every rejoin. They are runtime state in the
 * sense that they are *derived* from the level rather than being the level — but a derived value the
 * player can only earn by mining or killing is not something to hand back for free.
 */
public record Extras(List<String> disabled, List<net.minecraft.world.item.ItemStack> cache,
                     Map<String, Integer> progress) {

    public static final Extras EMPTY = new Extras(List.of(), List.of(), Map.of());

    /** Key for the breaking fist's counter, per tier index. */
    public static String miningKey(int tier) {
        return "fist_mining_" + tier;
    }

    /** Key for the punching fist's counter, per stage index. */
    public static String punchingKey(int stage) {
        return "fist_punching_" + stage;
    }

    public static final Codec<Extras> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("disabled", List.of()).forGetter(Extras::disabled),
            net.minecraft.world.item.ItemStack.CODEC.listOf().optionalFieldOf("cache", List.of())
                    .forGetter(Extras::cache),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("progress", Map.of())
                    .forGetter(Extras::progress)
    ).apply(instance, Extras::new));
}
