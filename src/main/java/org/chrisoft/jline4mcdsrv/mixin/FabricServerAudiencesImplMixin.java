package org.chrisoft.jline4mcdsrv.mixin;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.platform.fabric.FabricAudiences;
import net.kyori.adventure.platform.fabric.impl.server.FabricServerAudiencesImpl;
import net.minecraft.commands.CommandSource;
import net.minecraft.server.dedicated.DedicatedServer;
import org.chrisoft.jline4mcdsrv.CommandSourceAudience;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FabricServerAudiencesImpl.class, remap = false)
abstract class FabricServerAudiencesImplMixin {
    // ew
    @Inject(method = "audience(Lnet/minecraft/class_2165;)Lnet/kyori/adventure/audience/Audience;", at = @At("HEAD"), cancellable = true)
    private void injectAudience(final CommandSource source, final CallbackInfoReturnable<Audience> cir) {
        if (source instanceof DedicatedServer) {
            cir.setReturnValue(new CommandSourceAudience(source, (FabricAudiences) this));
        }
    }
}
