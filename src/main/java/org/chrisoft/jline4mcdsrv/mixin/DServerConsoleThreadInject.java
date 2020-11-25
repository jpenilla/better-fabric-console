package org.chrisoft.jline4mcdsrv.mixin;

import org.chrisoft.jline4mcdsrv.JLineForMcDSrvMain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = {"net.minecraft.server.dedicated.MinecraftDedicatedServer$1"})
public abstract class DServerConsoleThreadInject
{
    @Inject(at = @At("HEAD"), method = "run()V", cancellable = true)
    private void consoleThreadQuit(CallbackInfo info)
    {
        JLineForMcDSrvMain.LOGGER.info("Vanilla console thread stopped.");
        info.cancel();
    }
}
