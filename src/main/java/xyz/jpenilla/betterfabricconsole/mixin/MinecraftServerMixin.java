/*
 * This file is part of Better Fabric Console, licensed under the MIT License.
 *
 * Copyright (c) 2021-2024 Jason Penilla
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package xyz.jpenilla.betterfabricconsole.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.kyori.adventure.platform.modcommon.MinecraftServerAudiences;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xyz.jpenilla.endermux.log4j.RichLogContext;

@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {
  @WrapOperation(
    method = "sendSystemMessage",
    at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Component;getString()Ljava/lang/String;")
  )
  private String wrapMessage(
    final Component message,
    final Operation<String> original,
    @Share("BFC_systemMessageScope") final LocalRef<RichLogContext.Scope> systemMessageScope
  ) {
    if ((Object) this instanceof DedicatedServer dedicated) {
      final MinecraftServerAudiences audiences = MinecraftServerAudiences.of(dedicated);
      final net.kyori.adventure.text.Component adventureMessage = audiences.asAdventure(message);
      systemMessageScope.set(RichLogContext.pushComponent(adventureMessage));
    }
    return original.call(message);
  }

  @WrapOperation(
    method = "sendSystemMessage",
    at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;info(Ljava/lang/String;)V")
  )
  private void wrapLogInfo(
    final Logger logger,
    final String message,
    final Operation<Void> original,
    @Share("BFC_systemMessageScope") final LocalRef<RichLogContext.Scope> systemMessageScope
  ) {
    try {
      original.call(logger, message);
    } finally {
      final RichLogContext.Scope scope = systemMessageScope.get();
      if (scope != null) {
        systemMessageScope.set(null);
        scope.close();
      }
    }
  }
}
