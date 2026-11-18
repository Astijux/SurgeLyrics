package dev.surgelyrics.mc1_21_11;

import dev.surgelyrics.core.ClickCounter;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/** One place that watches the mouse buttons, shared by the keystrokes and CPS widgets (menu clicks don't count). */
final class Clicks {
    private Clicks() {}

    static final ClickCounter L = new ClickCounter(), R = new ClickCounter();
    private static boolean leftDown, rightDown;

    static boolean leftDown() { return leftDown; }
    static boolean rightDown() { return rightDown; }

    static void poll(MinecraftClient mc, long nowMs) {
        boolean noScreen = mc.currentScreen == null;
        long win = mc.getWindow().getHandle();
        boolean l = noScreen && GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean r = noScreen && GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (l && !leftDown) L.click(nowMs);
        if (r && !rightDown) R.click(nowMs);
        leftDown = l;
        rightDown = r;
    }
}
