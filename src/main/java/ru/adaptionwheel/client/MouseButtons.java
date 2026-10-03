package ru.adaptionwheel.client;

import net.minecraft.client.input.MouseButtonEvent;

/**
 * Which number a mouse button has.
 *
 * <p><b>26.3 renumbered the mouse buttons and nothing about the shape of the API says so.</b> The
 * old numbering was GLFW's, where the left button was 0; on this branch it is 1, the middle button
 * is 2 and the right button is 3 — which is what vanilla's own {@code InputConstants.Type.MOUSE}
 * table says ({@code key.mouse.left} = 1, {@code key.mouse.right} = 3), and what
 * {@code AbstractContainerScreen} assumes when it maps a click back to a container button
 * ({@code case 1 -> 0}, {@code case 3 -> 1}).</p>
 *
 * <p>This cost an afternoon: both screens here were ported from 1.21.1 asking "is this button
 * <em>0</em>", which on this branch no left click ever answers. The adaptation browser's tabs and
 * the trading blocks' rows did nothing at all, silently, and the click fell through to vanilla,
 * whose {@code mouseClicked} returns {@code true} unconditionally — so it looked like the game had
 * eaten the click rather than like the mod had misread it.</p>
 *
 * <p>So: never write the literal. Ask here, and the port to {@code main} is one file.</p>
 */
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
