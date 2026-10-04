package ru.adaptionwheel;

import ru.adaptionwheel.client.ClientAdaption;
import ru.adaptionwheel.category.Concepts;

final class ClientChecks {

    private ClientChecks() {
    }

    static boolean has(String concept) {
        return ClientAdaption.wearingWheel && ClientAdaption.active(concept);
    }

    static int level(String concept) {
        return ClientAdaption.wearingWheel ? ClientAdaption.levelOrZero(concept) : 0;
    }
}
