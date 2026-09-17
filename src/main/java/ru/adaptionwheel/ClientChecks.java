package ru.adaptionwheel;

import ru.adaptionwheel.client.ClientAdaption;
import ru.adaptionwheel.category.Concepts;

/** Client-only half of {@link SurfaceAdaptations}; never referenced on the server. */
final class ClientChecks {

    private ClientChecks() {
    }

    static boolean has(String concept) {
        return ClientAdaption.wearingWheel && ClientAdaption.isAdapted(concept);
    }

    static int level(String concept) {
        return ClientAdaption.wearingWheel ? ClientAdaption.LEVELS.getOrDefault(concept, 0) : 0;
    }
}
