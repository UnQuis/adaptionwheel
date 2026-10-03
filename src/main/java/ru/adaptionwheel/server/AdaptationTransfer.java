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

            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    public static boolean transfer(ServerPlayer donor, ServerPlayer recipient) {
        PlayerAdaption from = AdaptionEvents.dataOf(donor);
        PlayerAdaption to = AdaptionEvents.dataOf(recipient);
        String concept = bestOffer(from, to);
        if (concept == null) {
            donor.displayClientMessage(Component.translatable("adaptionwheel.msg.transfer_nothing",
                    recipient.getName()).withStyle(ChatFormatting.GRAY), false);
            return false;
        }
        int level = from.level(concept);

        if (!AdaptionEvents.debugUngrant(donor, concept)) {
            return false;
        }
        AdaptionEvents.debugGrant(recipient, concept, level);

        Component name = Concepts.chatName(concept);
        recipient.displayClientMessage(Component.translatable("adaptionwheel.msg.transfer_received",
                        name, donor.getName())
                .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(
                        net.minecraft.network.chat.TextColor.fromRgb(Concepts.color(concept)))
                        .withBold(true)), false);
        donor.displayClientMessage(Component.translatable("adaptionwheel.msg.transfer_given",
                        name, recipient.getName())
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        recipient.level().playSound(null, recipient.blockPosition(), ModSounds.REF.get(),
                SoundSource.PLAYERS, 0.7f, 1.3f);
        AdaptionEvents.syncAdaption(recipient, to,
                ru.adaptionwheel.SurfaceAdaptations.wearingWheel(recipient));
        return true;
    }

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

    private static int rank(PlayerAdaption data, String concept) {
        int level = data.level(concept);
        return level > 0 ? level : PlayerAdaption.MAX_LEVEL + 1;
    }

    private static boolean holds(PlayerAdaption data, String concept) {
        return data.isAdapted(concept) || data.level(concept) > 0;
    }
}
