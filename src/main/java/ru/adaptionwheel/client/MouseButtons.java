package ru.adaptionwheel.client;

import net.minecraft.client.input.MouseButtonEvent;

public final class MouseButtons {

    public static final int LEFT = 1;
    public static final int MIDDLE = 2;
    public static final int RIGHT = 3;

    private MouseButtons() {
    }

    public static boolean isLeft(MouseButtonEvent event) {
        return event.button() == LEFT;
    }

    public static boolean isRight(MouseButtonEvent event) {
        return event.button() == RIGHT;
    }
}
