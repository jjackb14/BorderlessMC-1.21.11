package com.jjackb14.borderlessmc.mixin;

import com.jjackb14.borderlessmc.config.BorderlessConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.IntBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * Mixin that replaces Minecraft's exclusive fullscreen behavior with borderless windowed
 * fullscreen.
 *
 * <p>When the user toggles fullscreen with (F11), this mixin:</p>
 * <ul>
 *     <li>Removes window decorations (border and title bar)</li>
 *     <li>Keeps the GLFW window in windowed mode (no exclusive monitor lock)</li>
 *     <li>Resizes and positions the window to fully cover the monitor the window is currently on</li>
 *     <li>Forces Minecraft's internal fullscreen state back to {@code false} so the Video Settings UI stays
 *     in sync</li>
 * </ul>
 *
 * <p>This avoids exclusive fullscreen issues such as slow alt-tab behavior, monitor switching
 * glitches, and OS interruptions, while still appearing visually fullscreen.</p>
 *
 * <p>This Mixin is client-only.</p>
 *
 * @author jjackb14
 */
@Mixin(Window.class)
public abstract class WindowMixin {

    /**
     * Shadow Minecraft's getHandle method to determine the window.
     *
     * @return the window Minecraft is on.
     */
	@Shadow public abstract long getHandle();

    /**
     * Shadow Minecraft's isFullscreen method to check if Minecraft is in fullscreen.
     *
     * @return a boolean value representing if Minecraft is in fullscreen or not.
     */
    @Shadow public abstract boolean isFullscreen();

    /**
     * Minecraft's internal fullscreen flag.
     *
     * <p>This mod forces it to {@code false} after entering borderless mode so the game
     * does not consider itself in exclusive fullscreen (and the UI can remain consistent).</p>
     */
    @Shadow private boolean fullscreen;

    /**
     * Last known windowed X position before entering borderless fullscreen.
     */
    private int prevX;

    /**
     * Last known windowed Y position before entering borderless fullscreen.
     */
    private int prevY;

    /**
     * Last known windowed widith before entering borderless fullscreen.
     */
    private int prevWidth;

    /**
     * Last known windowed height before entering borderless fullscreen.
     */
    private int prevHeight;

    /**
     * Whether a valid windowed state has been captured.
     */
    private boolean hasSavedWindowedState = false;

    /**
     * True if the game is currently in borderless-windowed "fullscreen".
     */
    private boolean borderlessActive = false;

    /**
     * Handles the "exit borderless" path.
     *
     * <p>If borderless mode is currently active, the next fullscreen toggle request
     * (typically F11) is treated as an exit action. Vanilla fullscreen toggling is
     * cancelled to prevent Minecraft from immediately re-entering fullscreen.</p>
     *
     * @param ci mixin callback info used to cancel vanilla execution
     */
    @Inject(method = "toggleFullscreen", at = @At("HEAD"), cancellable = true)
    private void borderless_onToggleFullscreenHead(CallbackInfo ci) {
        if (!borderlessActive) return;

        long window = getHandle();
        exitBorderless(window);

        borderlessActive = false;

        // Keep Minecraft internally consistent
        this.fullscreen = false;
        trySetFullscreenOption(MinecraftClient.getInstance());

        // Stop vanilla toggleFullscreen from running
        ci.cancel();
    }

    /**
     * Handles the "enter borderless" path.
     *
     * <p>After vanilla toggles fullscreen ON, this converts the window into borderless
     * windowed fullscreen, sets {@code borderlessActive}, and forces Minecraft's internal
     * fullscreen state back to windowed.</p>
     *
     * @param ci mixin callback info
     */
    @Inject(method = "toggleFullscreen", at = @At("TAIL"))
    private void borderless_onToggleFullscreenTail(CallbackInfo ci) {
        // If vanilla didn't just enter fullscreen, do nothing.
        if (!isFullscreen()) return;

        if (!BorderlessConfig.get().enabled) return;

        long window = getHandle();
        enterBorderless(window);

        borderlessActive = true;

        // Make Minecraft treat this as windowed (we're borderless windowed, not exclusive fullscreen)
        this.fullscreen = false;
        trySetFullscreenOption(MinecraftClient.getInstance());
    }

    /**
     * Converts the current GLFW window into borderless windowed fullscreen.
     *
     * <p>This method:</p>
     * <ul>
     *   <li>Saves the current windowed position and size for later restoration.</li>
     *   <li>Disables window decorations (border/titlebar).</li>
     *   <li>Resizes and repositions the window to fully cover the chosen monitor.</li>
     * </ul>
     *
     * @param window GLFW window handle
     */
    private void enterBorderless(long window) {
        saveWindowedState(window);

        GLFW.glfwSetWindowAttrib(window, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);

        long monitor = findMonitor(window);
        if (monitor == 0L) monitor = GLFW.glfwGetPrimaryMonitor();

        GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
        if (mode == null) return;

        int mx, my;
        try (var stack = stackPush()) {
            IntBuffer px = stack.mallocInt(1);
            IntBuffer py = stack.mallocInt(1);
            GLFW.glfwGetMonitorPos(monitor, px, py);
            mx = px.get(0);
            my = py.get(0);
        }

        // Windowed monitor (0) + sized to monitor => borderless fullscreen effect
        GLFW.glfwSetWindowMonitor(window, 0, mx, my, mode.width(), mode.height(), mode.refreshRate());
    }

    /**
     * Restores normal windowed mode from borderless fullscreen.
     *
     * <p>This method re-enables window decorations and restores the last saved
     * windowed size and position, if available.</p>
     *
     * @param window GLFW window handle
     */
    private void exitBorderless(long window) {
        // Restore decorations first
        GLFW.glfwSetWindowAttrib(window, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);

        if (!hasSavedWindowedState) return;

        // Use SetWindowMonitor to force it back to a normal windowed size/pos in one call
        GLFW.glfwSetWindowMonitor(window, 0, prevX, prevY, prevWidth, prevHeight, GLFW.GLFW_DONT_CARE);
    }

    /**
     * Captures the current window position and size for restoration when exiting
     * borderless fullscreen.
     *
     * <p>The saved values are taken immediately before the window is resized to
     * cover a monitor.</p>
     *
     * @param window GLFW window handle
     */
    private void saveWindowedState(long window) {
        try (var stack = stackPush()) {
            IntBuffer x = stack.mallocInt(1);
            IntBuffer y = stack.mallocInt(1);
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);

            GLFW.glfwGetWindowPos(window, x, y);
            GLFW.glfwGetWindowSize(window, w, h);

            prevX = x.get(0);
            prevY = y.get(0);
            prevWidth = w.get(0);
            prevHeight = h.get(0);
            hasSavedWindowedState = true;
        }
    }

    /**
     * Determines the monitor the window is currently on.
     *
     * <p>The monitor is selected by checking which monitor contains the center point of the window.
     * This works correctly for multi-monitor setups with arbitrary layouts.</p>
     *
     * @param window GLFW window handle.
     *
     * @return the GLFW monitor handle, or {@code 0L} if no suitable monitor is found.
     */
    private long findMonitor(long window) {
        int wx, wy, ww, wh;

        try (var stack = stackPush()) {
            IntBuffer x = stack.mallocInt(1);
            IntBuffer y = stack.mallocInt(1);
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);

            GLFW.glfwGetWindowPos(window, x, y);
            GLFW.glfwGetWindowSize(window, w, h);

            wx = x.get(0);
            wy = y.get(0);
            ww = w.get(0);
            wh = h.get(0);
        }

        int cx = wx + ww / 2;
        int cy = wy + wh / 2;

        var monitors = GLFW.glfwGetMonitors();
        if (monitors == null || monitors.remaining() == 0) {
            return 0L;
        }

        long bestMonitor = 0L;

        for (int i = 0; i < monitors.limit(); i++) {
            long m = monitors.get(i);

            int mx, my;
            GLFWVidMode mode = GLFW.glfwGetVideoMode(m);
            if (mode == null) {
                continue;
            }

            try (var stack = stackPush()) {
                IntBuffer px = stack.mallocInt(1);
                IntBuffer py = stack.mallocInt(1);

                GLFW.glfwGetMonitorPos(m, px, py);

                mx = px.get(0);
                my = py.get(0);
            }

            int mw = mode.width();
            int mh = mode.height();

            if (cx >= mx && cx < mx + mw && cy >= my && cy < my + mh) {
                return m;
            }

            if (bestMonitor == 0L) {
                bestMonitor = m;
            }
        }

        return 0L;
    }

    /**
     * Attempts to synchronize Minecraft's Video Settings fullscreen option with the borderless windowed state.
     *
     * <p>This uses reflection to avoid hard dependencies on mapping-specific field or method names.
     * If this fails, the mod still functions correctly, but the fullscreen toggle in the options
     * menu may remain enabled.</p>
     *
     * @param client active Minecraft client instance.
     */
    @Unique
    private void trySetFullscreenOption(MinecraftClient client) {
        try {
            client.options.getClass().getField("fullscreen").get(client.options)
                    .getClass().getMethod("setValue", Object.class)
                    .invoke(client.options.getClass().getField("fullscreen").get(client.options), Boolean.FALSE);
        }
        catch (Throwable ignored) {
            // If reflection fails, it still works functionally; the menu toggle may stay "ON".
        }

        try {
            client.options.getClass().getMethod("write").invoke(client.options);
        }
        catch (Throwable ignored) {}
    }
}