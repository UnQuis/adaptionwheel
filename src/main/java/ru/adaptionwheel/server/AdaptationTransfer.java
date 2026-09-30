package ru.adaptionwheel.server;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.item.MahoragaWheelItem;
import ru.adaptionwheel.sound.ModSounds;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Handing an adaptation to another player.
 *
 * <p>The wheel's data already travels with the item, which is what made this nearly free: a wheel
 * handed over whole brings its owner's progress with it, and the only thing missing was a way to
 * move a single adaptation without also moving everything else. This is that way.</p>
 *
 * <p>Right-clicking another player with the wheel in hand offers them the most developed adaptation
 * you hold that they do not. "Most developed" is a single descending order, so repeated clicks walk
 * down your own list and stop when there is nothing left to give — a player can therefore hand
 * over exactly as much or as little as they want without ever being asked to pick from a list.</p>
 *
 * <p>No menu, and deliberately so: a screen here would be the mod's first container GUI, and a
 * picker for thirty concepts is worse than a predictable order. What is transferred is the
 * recipient's gain and the donor's loss at the same level, so nothing is invented and nothing is
 * rounded in either direction.</p>
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID)
public final class AdaptationTransfer {

    private AdaptationTransfer() {
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer donor)) {
            return;
        }
        if (!AdaptionConfig.TRANSFER_ENABLED.get()) {
            return;
        }
        if (!MahoragaWheelItem.isWheel(event.getItemStack())) {
            return;
        }
        if (!(event.getTarget() instanceof ServerPlayer recipient) || recipient == donor) {
            return;
        }
        if (transfer(donor, recipient)) {
            // Consume the click: without this the interaction falls through to the target's own
            // handling, which for a player is nothing, but a modded target could act on it.
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    /**
     * Moves one adaptation from donor to recipient.
     *
     * @return true when something actually moved
     */
    public static boolean transfer(ServerPlayer donor, ServerPlayer recipient) {
        PlayerAdaption from = AdaptionEvents.dataOf(donor);
        PlayerAdaption to = AdaptionEvents.dataOf(recipient);
        String concept = bestOffer(from, to);
        if (concept == null) {
            donor.sendSystemMessage(Component.translatable("adaptionwheel.msg.transfer_nothing",
                    recipient.getName()).withStyle(ChatFormatting.GRAY), false);
            return false;
        }
        int level = from.level(concept);

        if (!AdaptionEvents.debugUngrant(donor, concept)) {
            return false;
        }
        AdaptionEvents.debugGrant(recipient, concept, level);

        Component name = Concepts.chatName(concept);
        recipient.sendSystemMessage(Component.translatable("adaptionwheel.msg.transfer_received",
                        name, donor.getName())
                .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(
                        net.minecraft.network.chat.TextColor.fromRgb(Concepts.color(concept)))
                        .withBold(true)), false);
        donor.sendSystemMessage(Component.translatable("adaptionwheel.msg.transfer_given",
                        name, recipient.getName())
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        recipient.level().playSound(null, recipient.blockPosition(), ModSounds.REF.get(),
                SoundSource.PLAYERS, 0.7f, 1.3f);
        AdaptionEvents.sync(recipient, to,
                ru.adaptionwheel.SurfaceAdaptations.wearingWheel(recipient));
        return true;
    }

    /**
     * The most developed adaptation the donor holds that the recipient lacks, or {@code null}.
     *
     * <p>Ordering, and why a one-time adaptation outranks everything: adapting to a boss, or to
     * fire, or to the void is a completed achievement, while a level-3 contact is three quarters of
     * one. Transferring should hand over the thing that was hardest to earn first, and a player who
     * wants to give away their small stuff can simply keep clicking until they reach it.</p>
     *
     * <p>Concept name is the tie-break, so the order is total and does not depend on hash
     * iteration order — otherwise two players with identical adaptation sets could disagree about
     * what the next click gives, and the second click would look like it gave a different
     * adaptation for no reason.</p>
     */
    public static String bestOffer(PlayerAdaption donor, PlayerAdaption recipient) {
        List<String> candidates = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : donor.levels.entrySet()) {
            if (entry.getValue() > 0 && !holds(recipient, entry.getKey())) {
                candidates.add(entry.getKey());
            }
        }
        for (String adapted : donor.adapted) {
            if (!holds(recipient, adapted)) {
                candidates.add(adapted);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort(Comparator
                .comparingInt((String c) -> -rank(donor, c))
                .thenComparing(Comparator.naturalOrder()));
        return candidates.get(0);
    }

    /** How developed a concept is; higher wins. One-time adaptations sit above every level. */
    private static int rank(PlayerAdaption data, String concept) {
        int level = data.level(concept);
        return level > 0 ? level : PlayerAdaption.MAX_LEVEL + 1;
    }

    private static boolean holds(PlayerAdaption data, String concept) {
        return data.isAdapted(concept) || data.level(concept) > 0;
    }
}
