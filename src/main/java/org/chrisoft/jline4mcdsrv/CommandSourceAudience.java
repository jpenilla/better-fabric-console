package org.chrisoft.jline4mcdsrv;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.platform.fabric.FabricAudiences;
import net.kyori.adventure.text.Component;
import net.minecraft.commands.CommandSource;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * copy pasta of a class from adventure-platform-fabric, because it's not visible and this is easy
 */
public final class CommandSourceAudience implements Audience {
    private final CommandSource output;
    private final FabricAudiences serializer;

    public CommandSourceAudience(final @NonNull CommandSource output, final @NonNull FabricAudiences serializer) {
        this.output = output;
        this.serializer = serializer;
    }

    @Override
    public void sendMessage(final @NonNull Identity source, final @NonNull Component text, final @NonNull MessageType type) {
        this.output.sendMessage(this.serializer.toNative(text), source.uuid());
    }

    @Override
    public void sendActionBar(final @NonNull Component message) {
        this.sendMessage(Identity.nil(), message);
    }
}
