package com.jjackb14.borderlessmc.mixin;

import com.jjackb14.borderlessmc.config.BorderlessConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.DebugHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.List;

@Mixin(DebugHud.class)
public abstract class DebugHudMixin {

    @Shadow private MinecraftClient client;

    @Inject(method = "render", at = @At("TAIL"))
    private void borderlessmc_renderStatus(DrawContext context, CallbackInfo ci) {
        if (this.client == null) return;

        int x = 2;
        int y = 2;

        List<String> leftLines = tryGetLeftDebugLines();
        if (leftLines != null) {
            int lineHeight = this.client.textRenderer.fontHeight + 2;
            y += leftLines.size() * lineHeight;
        }
        else {
            y += (this.client.textRenderer.fontHeight + 2) * 10;
        }

        boolean enabled = BorderlessConfig.get().enabled;
        Text line = Text.literal("BorderlessMC: " + (enabled ? "Enabled" : "Disabled"));

        context.drawCenteredTextWithShadow(this.client.textRenderer, line, x, y, 0xFFFFFF);
    }

    @SuppressWarnings("unchecked")
    private List<String> tryGetLeftDebugLines() {
        Object self = this; // the mixed-in DebugHud instance

        // Try common Yarn name first
        List<String> lines = tryInvokeListMethod(self, "getLeftText");
        if (lines != null) return lines;

        // Try common intermediary name used in many versions
        lines = tryInvokeListMethod(self, "method_1835");
        if (lines != null) return lines;

        return null;
    }

    @SuppressWarnings("unchecked")
    private List<String> tryInvokeListMethod(Object target, String name) {
        try {
            Method m = target.getClass().getDeclaredMethod(name);
            m.setAccessible(true);
            Object result = m.invoke(target);
            if (result instanceof List<?> list) {
                return (List<String>) list;
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
