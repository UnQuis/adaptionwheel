package ru.adaptionwheel.client;

import ru.adaptionwheel.network.AbilityCooldownPayload;

/** Client-side cooldown mirror stub (26.3 integration placeholder). */
public final class ClientCooldowns {
    private ClientCooldowns() {}

    public static void apply(AbilityCooldownPayload payload) {
        // TODO(port-26.3): wire into HUD/ability UI when client cooldown state is ported.
    }
}
