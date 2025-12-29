package com.jjackb14.borderlessmc;

import com.jjackb14.borderlessmc.config.BorderlessConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class BorderlessMCClient implements ClientModInitializer {

    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.borderlessmc.toggle",
                GLFW.GLFW_KEY_B,
                KeyBinding.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                BorderlessConfig cfg = BorderlessConfig.get();
                cfg.enabled = !cfg.enabled;
                BorderlessConfig.save();

                if (client.player != null) {
                    client.player.sendMessage(
                            Text.literal("BorderlessMC: " + (cfg.enabled ? "Enabled" : "Disabled")),
                            true
                    );
                }
            }
        });
    }
}
