package org.chrisoft.jline4mcdsrv.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.apache.logging.log4j.LogManager;

@Mixin(targets = {"net.minecraft.server.dedicated.MinecraftDedicatedServer$1"})
public class DServerConsoleThreadInject {
    @Inject(
            at = @At("HEAD"),
            method = "run()V",
            cancellable = true)
    private void consoleThreadQuit(CallbackInfo info)
    {
        LogManager.getLogger().info("Vanilla console thread stopped.");
        info.cancel();
    }
}
