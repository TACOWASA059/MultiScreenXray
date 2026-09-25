package com.github.tacowasa059.multiscreenxray.mixin;

import com.github.tacowasa059.multiscreenxray.client.window.MultiWindowManager;
import com.mojang.blaze3d.platform.Window;
import org.lwjgl.sdl.SDL_Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public abstract class WindowEventMixin {
    @Inject(method = "handleEvent", at = @At("HEAD"), cancellable = true)
    private void multiscreenxray$handleExtraWindowEvent(SDL_Event event, CallbackInfo ci) {
        if (MultiWindowManager.handleWindowEvent(event)) ci.cancel();
    }
}