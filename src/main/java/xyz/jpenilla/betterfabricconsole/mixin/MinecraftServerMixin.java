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
import java.util.Locale;
import net.kyori.adventure.platform.modcommon.MinecraftServerAudiences;
import net.kyori.adventure.text.renderer.ComponentRenderer;
import net.kyori.adventure.text.serializer.ansi.ANSIComponentSerializer;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.translation.Translator;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import org.apache.logging.log4j.ThreadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.jpenilla.betterfabricconsole.BetterFabricConsole;
import xyz.jpenilla.endermux.server.log.RemoteLogForwarder;
import xyz.jpenilla.endermux.server.util.LanguageRenderer;

@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {
  @WrapOperation(
    method = "sendSystemMessage",
    at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Component;getString()Ljava/lang/String;")
  )
  private String wrapMessage(final Component message, final Operation<String> original) {
    if ((Object) this instanceof DedicatedServer dedicated) {
      final MinecraftServerAudiences audiences = MinecraftServerAudiences.of(dedicated);
      final net.kyori.adventure.text.Component adventureMessage = audiences.asAdventure(message);

      if (BetterFabricConsole.instance().config().consoleSocket().enabled()) {
        final ComponentRenderer<Locale> renderer = new LanguageRenderer(new LanguageRenderer.LanguageProxy() {
          @Override
          public boolean has(final String key) {
            return Language.getInstance().has(key);
          }

          @Override
          public String getOrDefault(final String key, final String fallback) {
            return Language.getInstance().getOrDefault(key, fallback);
          }
        });
        ThreadContext.put(RemoteLogForwarder.COMPONENT_LOG_MESSAGE_KEY, GsonComponentSerializer.gson().serialize(
          renderer.render(adventureMessage, Translator.parseLocale("en_us"))
        ));
      }

      return ANSIComponentSerializer.ansi().serialize(adventureMessage);
    } else {
      return original.call(message);
    }
  }

  @Inject(
    at = @At(
      value = "INVOKE",
      target = "Lorg/slf4j/Logger;info(Ljava/lang/String;)V",
      shift = At.Shift.AFTER
    ),
    method = "sendSystemMessage"
  )
  private void afterLog(final Component message, final CallbackInfo ci) {
    if ((Object) this instanceof DedicatedServer) {
      ThreadContext.remove(RemoteLogForwarder.COMPONENT_LOG_MESSAGE_KEY);
    }
  }
}
