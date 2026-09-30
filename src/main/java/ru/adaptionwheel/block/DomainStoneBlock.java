package ru.adaptionwheel.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import ru.adaptionwheel.adapt.AdaptationDefinition;
import ru.adaptionwheel.adapt.AdaptationRegistry;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.category.WheelTier;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.server.AdaptionEvents;
import ru.adaptionwheel.sound.ModSounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Domain Stone: right-clicked with the wheel worn, it hands over an adaptation outright.
 *
 * <p>The wheel's entire progression is "endure this until you no longer care about it", which means
 * every one of its fifty-odd capabilities is bought with suffering and time. This is the one place
 * an adaptation is simply given, and it is the reason the mod needed a world at all: a block worth
 * walking to, doing something to, and walking away from changed by.</p>
 *
 * <p>What it gives is drawn from what the wheel has <em>not</em> revealed yet at the player's tier
 * first, and only falls back to anything unfinished if that pool is empty. So it is a shortcut
 * towards the families the next tier opens rather than a way to skip past them.</p>
 *
 * <p>A cooldown, per player, is pacing rather than a price: the pool excludes everything already
 * held, so a player at a stone can drain the rest of the mod in a few clicks otherwise, and what
 * makes the block interesting is that it has something left to give next time.</p>
 */
public class DomainStoneBlock extends Block {

    private static final Map<UUID, Long> NEXT_USE = new ConcurrentHashMap<>();

    public DomainStoneBlock() {
        super(ModBlocks.base(MapColor.COLOR_BLACK, 6.0F, 12.0F).lightLevel(state -> 4));
    }

    public static void forget(UUID id) {
        NEXT_USE.remove(id);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer server)) {
            return InteractionResult.PASS;
        }
        if (!AdaptionConfig.DOMAIN_STONE_ENABLED.get()) {
            return InteractionResult.PASS;
        }
        // Worn, not held: the wheel lives in a Curios slot, so "is the wheel in your hand" would
        // be false for every ordinary player in the game.
        if (!ru.adaptionwheel.SurfaceAdaptations.wearingWheel(server)) {
            server.displayClientMessage(Component.translatable("adaptionwheel.msg.stone_no_wheel")
                    .withStyle(ChatFormatting.GRAY), false);
            return InteractionResult.PASS;
        }
        long now = level.getGameTime();
        Long next = NEXT_USE.get(server.getUUID());
        if (next != null && now < next) {
            server.displayClientMessage(Component.translatable("adaptionwheel.msg.stone_waiting",
                            Math.max(1, (int) ((next - now + 19) / 20)))
                    .withStyle(ChatFormatting.DARK_GRAY), false);
            return InteractionResult.PASS;
        }

        PlayerAdaption data = AdaptionEvents.dataOf(server);
        String concept = choose(data, WheelTier.forCount(data.getAdaptCount()));
        if (concept == null) {
            server.displayClientMessage(Component.translatable("adaptionwheel.msg.stone_nothing")
                    .withStyle(ChatFormatting.GRAY), false);
            return InteractionResult.PASS;
        }

        NEXT_USE.put(server.getUUID(), now + AdaptionConfig.DOMAIN_STONE_COOLDOWN_SECONDS.get() * 20L);
        boolean leveled = Concepts.isLevelBased(concept);
        AdaptionEvents.debugGrant(server, concept,
                leveled ? PlayerAdaption.MAX_LEVEL : 0);

        server.displayClientMessage(Component.translatable("adaptionwheel.msg.stone_given",
                                        Concepts.chatName(concept))
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(Concepts.color(concept)))
                        .withBold(true)), false);
        level.playSound(null, pos, ModSounds.REF.get(), SoundSource.BLOCKS, 0.9f, 0.7f);
        level.playSound(null, pos, ModSounds.ADAPT_VOICE.get(), SoundSource.BLOCKS, 0.6f, 1.4f);
        return InteractionResult.CONSUME;
    }

    /**
     * Picks the adaptation the stone hands over: an unrevealed family first, then anything else
     * unfinished, and {@code null} when there is nothing left to learn at all.
     *
     * <p>Public and static so the choice can be tested without a world. Never offers
     * {@code ADBERSITY}: it is a survival challenge rather than an adaptation, and being handed it
     * would start a timed event the player did not ask for.</p>
     */
    public static String choose(PlayerAdaption data, int tier) {
        List<String> unrevealed = new ArrayList<>();
        List<String> unfinished = new ArrayList<>();
        for (AdaptationDefinition definition : AdaptationRegistry.allDefinitions()) {
            String concept = definition.concept();
            if (concept.equals(Concepts.ADVERSITY)) {
                continue;
            }
            if (data.isAdapted(concept) || data.level(concept) > 0) {
                continue;
            }
            if (WheelTier.familyUnlocked(concept, tier)) {
                unfinished.add(concept);
            } else {
                unrevealed.add(concept);
            }
        }
        List<String> pool = unrevealed.isEmpty() ? unfinished : unrevealed;
        if (pool.isEmpty()) {
            return null;
        }
        // ThreadLocalRandom, not RandomSource: there is no world or entity to draw a seeded
        // RandomSource from here, and a grant does not need to be reproducible.
        return pool.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(pool.size()));
    }
}
