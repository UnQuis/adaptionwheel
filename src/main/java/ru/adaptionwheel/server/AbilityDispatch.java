package ru.adaptionwheel.server;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import ru.adaptionwheel.ability.ActiveAbility;
import ru.adaptionwheel.ability.ActiveAbilities;
import ru.adaptionwheel.data.PlayerAdaption;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The single active-ability slot: one key, one chosen ability, server-authoritative.
 *
 * <p>The menu used to call {@code ActiveAbilities.select(id)} locally, and the R key was wired
 * straight to {@code DivePayload}. That works while there is one ability, but the moment a second
 * one appears the player has to remember which key belongs to which — and a client-only selection
 * would let the HUD advertise an ability the server refuses. Here the selection lives in the
 * player's persisted data, is validated against the same {@code unlocked} predicate the sync list
 * uses, and the activation key only says "use whatever is in the slot".
 *
 * <p>Registration is open so a new ability needs a definition (for the menu/HUD) and one call here
 * (for the server behaviour); no new keybind or packet is needed.
 */
public final class AbilityDispatch {

    private record Entry(Consumer<ServerPlayer> activate, Predicate<PlayerAdaption> unlock) {
    }

    private static final Map<String, Entry> HANDLERS = new ConcurrentHashMap<>();

    private AbilityDispatch() {
    }

    public static void register(String id, Consumer<ServerPlayer> activate, Predicate<PlayerAdaption> unlock) {
        if (id == null || id.isBlank() || activate == null || unlock == null) {
            return;
        }
        HANDLERS.put(id, new Entry(activate, unlock));
    }

    public static boolean isUnlocked(String id, PlayerAdaption data) {
        if (id == null || id.isBlank() || data == null) {
            return false;
        }
        Entry entry = HANDLERS.get(id);
        return entry != null && entry.unlock().test(data);
    }

    /**
     * The selected id if the player still qualifies for it, otherwise the first registered unlocked
     * ability. The fallback matters for old saves (empty selection) and for a switch being turned
     * off: the slot must not go dead when its ability closes its own gate.
     */
    @Nullable
    public static String resolveSelected(PlayerAdaption data) {
        if (data == null) {
            return null;
        }
        String id = data.selectedAbility();
        if (!id.isBlank() && isUnlocked(id, data)) {
            return id;
        }
        for (ActiveAbility ability : ActiveAbilities.all()) {
            if (isUnlocked(ability.id(), data)) {
                return ability.id();
            }
        }
        return null;
    }

    /** Server-side selection. Never trust an id the client sent without re-checking the gate. */
    public static boolean select(ServerPlayer player, String id) {
        if (player == null) {
            return false;
        }
        PlayerAdaption data = AdaptionEvents.dataOf(player);
        if (!isUnlocked(id, data)) {
            return false;
        }
        data.selectAbility(id);
        // The 1 Hz sync alone would leave the HUD showing the old ability for a second.
        AdaptionEvents.syncAdaption(player, data, AdaptionEvents.isWearingWheel(player));
        return true;
    }

    /** Called by the universal activation key. Every condition is re-checked here. */
    public static void activate(ServerPlayer player) {
        if (player == null || !AdaptionEvents.isWearingWheel(player)) {
            return;
        }
        PlayerAdaption data = AdaptionEvents.dataOf(player);
        String id = resolveSelected(data);
        if (id == null) {
            return;
        }
        Entry entry = HANDLERS.get(id);
        if (entry != null) {
            entry.activate().accept(player);
        }
    }
}
