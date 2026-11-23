/*
 * This file is part of Better Fabric Console, licensed under the MIT License.
 *
 * Copyright (c) 2026 Jason Penilla
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
package xyz.jpenilla.betterfabricconsole.console;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.permission.v1.PermissionContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SuggestionBox;
import net.minecraft.commands.suggestions.SuggestionContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@link SuggestionContext} for the server console, modeled on vanilla's {@code ServerPlayerSuggestionContext}.
 */
@NullMarked
public final class ConsoleSuggestionContext implements SuggestionContext {
  private final MinecraftServer server;
  private final CommandSourceStack source;
  private final HolderLookup.Provider filteredProvider;

  private ConsoleSuggestionContext(final MinecraftServer server) {
    this.server = server;
    this.source = server.createCommandSourceStack();
    this.filteredProvider = server.reloadableRegistries().lookup().filterFeatures(this.source.enabledFeatures());
  }

  public static SuggestionBox.Result parse(final MinecraftServer server, final StringReader reader) {
    return server.getCommands().suggestionBox().parse(reader, new ConsoleSuggestionContext(server));
  }

  @Override
  public PermissionSet permissions() {
    return this.source.permissions();
  }

  @Override
  public PermissionContext getPermissionContext() {
    return this.source.getPermissionContext();
  }

  @Override
  public Vec3 position() {
    return this.source.getPosition();
  }

  @Override
  public ServerLevel level() {
    return this.source.getLevel();
  }

  @Override
  public FeatureFlagSet enabledFeatures() {
    return this.source.enabledFeatures();
  }

  @Override
  public @Nullable Entity entity() {
    return this.source.getEntity();
  }

  @Override
  public MinecraftServer server() {
    return this.server;
  }

  @Override
  public Collection<String> onlinePlayerNames() {
    return List.of(this.server.getPlayerNames());
  }

  @Override
  public Collection<String> allTeams() {
    return this.server.getScoreboard().getTeamNames();
  }

  @Override
  public Stream<Identifier> availableSounds() {
    return BuiltInRegistries.SOUND_EVENT.stream().map(SoundEvent::location);
  }

  @Override
  public Stream<Identifier> availablePostEffects() {
    return Stream.empty();
  }

  @Override
  public CompletableFuture<Suggestions> suggestObjectiveNames(final CommandContext<?> context, final SuggestionsBuilder builder) {
    return SuggestionContext.suggest(this.server.getScoreboard().getObjectiveNames(), builder);
  }

  @Override
  public CompletableFuture<Suggestions> customSuggestion(final CommandContext<?> context) {
    return Suggestions.empty();
  }

  @Override
  public <E> CompletableFuture<Suggestions> suggestRegistryElements(
    final CommandContext<?> context,
    final ResourceKey<? extends Registry<? extends E>> key,
    final ElementSuggestionType elements,
    final SuggestionsBuilder builder,
    final Predicate<E> filter
  ) {
    return this.holderLookups().lookup(key).map(registry -> {
      this.suggestRegistryElements(registry, elements, builder, filter);
      return builder.buildFuture();
    }).orElseGet(Suggestions::empty);
  }

  @Override
  public Set<ResourceKey<Level>> levels() {
    return this.server.levelKeys();
  }

  @Override
  public HolderLookup.Provider holderLookups() {
    return this.filteredProvider;
  }
}
