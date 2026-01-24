package xyz.jpenilla.endermux.server.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.BlockNBTComponent;
import net.kyori.adventure.text.BuildableComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.EntityNBTComponent;
import net.kyori.adventure.text.KeybindComponent;
import net.kyori.adventure.text.NBTComponent;
import net.kyori.adventure.text.NBTComponentBuilder;
import net.kyori.adventure.text.ScoreComponent;
import net.kyori.adventure.text.SelectorComponent;
import net.kyori.adventure.text.StorageNBTComponent;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.VirtualComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.renderer.AbstractComponentRenderer;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;

/**
 * A component renderer that translates components using Minecraft's localization system.
 */
public final class LanguageRenderer extends AbstractComponentRenderer<Locale> {
  private static final Pattern LOCALIZATION_PATTERN = Pattern.compile("%(?:(\\d+)\\$)?s");
  private static final Set<Style.Merge> MERGES;

  static {
    final Set<Style.Merge> merges = EnumSet.allOf(Style.Merge.class);
    merges.remove(Style.Merge.EVENTS);
    MERGES = Collections.unmodifiableSet(merges);
  }

  @NullMarked
  public interface LanguageProxy {
    boolean has(String key);

    String getOrDefault(String key, String fallback);
  }

  private final LanguageProxy language;

  public LanguageRenderer(final LanguageProxy language) {
    this.language = language;
  }

  @Override
  protected @NonNull Component renderBlockNbt(final @NonNull BlockNBTComponent component, final @NonNull Locale context) {
    final BlockNBTComponent.Builder builder = this.nbt(context, Component.blockNBT(), component)
      .pos(component.pos());
    return this.mergeStyleAndOptionallyDeepRender(component, builder, context);
  }

  @Override
  protected @NonNull Component renderEntityNbt(final @NonNull EntityNBTComponent component, final @NonNull Locale context) {
    final EntityNBTComponent.Builder builder = this.nbt(context, Component.entityNBT(), component)
      .selector(component.selector());
    return this.mergeStyleAndOptionallyDeepRender(component, builder, context);
  }

  @Override
  protected @NonNull Component renderStorageNbt(final @NonNull StorageNBTComponent component, final @NonNull Locale context) {
    final StorageNBTComponent.Builder builder = this.nbt(context, Component.storageNBT(), component)
      .storage(component.storage());
    return this.mergeStyleAndOptionallyDeepRender(component, builder, context);
  }

  @Override
  protected @NonNull Component renderKeybind(final @NonNull KeybindComponent component, final @NonNull Locale context) {
    final KeybindComponent.Builder builder = Component.keybind().keybind(component.keybind());
    return this.mergeStyleAndOptionallyDeepRender(component, builder, context);
  }

  @Override
  protected @NonNull Component renderScore(final @NonNull ScoreComponent component, final @NonNull Locale context) {
    final ScoreComponent.Builder builder = Component.score()
      .name(component.name())
      .objective(component.objective())
      .value(component.value());
    return this.mergeStyleAndOptionallyDeepRender(component, builder, context);
  }

  @Override
  protected @NonNull Component renderSelector(final @NonNull SelectorComponent component, final @NonNull Locale context) {
    final SelectorComponent.Builder builder = Component.selector().pattern(component.pattern());
    return this.mergeStyleAndOptionallyDeepRender(component, builder, context);
  }

  @Override
  protected @NonNull Component renderText(final @NonNull TextComponent component, final @NonNull Locale context) {
    final TextComponent.Builder builder = Component.text().content(component.content());
    return this.mergeStyleAndOptionallyDeepRender(component, builder, context);
  }

  @Override
  protected @NonNull Component renderTranslatable(final @NonNull TranslatableComponent component, final @NonNull Locale context) {
    final List<TranslationArgument> arguments = component.arguments();
    final List<Component> children = component.children();

    final TranslatableComponent renderedComponent;
    if (!arguments.isEmpty() || !children.isEmpty()) {
      final TranslatableComponent.Builder builder = component.toBuilder();

      if (!arguments.isEmpty()) {
        final List<TranslationArgument> translatedArguments = new ArrayList<>(arguments);
        for (int i = 0; i < translatedArguments.size(); i++) {
          final TranslationArgument arg = translatedArguments.get(i);
          if (arg.value() instanceof Component value && !(arg.value() instanceof VirtualComponent)) {
            translatedArguments.set(i, TranslationArgument.component(this.render(value, context)));
          }
        }

        builder.arguments(translatedArguments);
      }

      renderedComponent = builder.build();
    } else {
      renderedComponent = component;
    }

    return this.renderTranslatableInner(renderedComponent, context);
  }

  private @NonNull Component renderTranslatableInner(final @NonNull TranslatableComponent component, final @NonNull Locale context) {
    final String key = component.key();
    if (!this.language.has(key)) {
      return component;
    }

    final String translated = this.language.getOrDefault(key, component.fallback());
    final Matcher matcher = LOCALIZATION_PATTERN.matcher(translated);
    final List<TranslationArgument> args = component.arguments();
    int argPosition = 0;
    int lastIdx = 0;

    final TextComponent.Builder builder = Component.text();
    this.mergeStyle(component, builder, context);

    while (matcher.find()) {
      if (lastIdx < matcher.start()) {
        builder.append(Component.text(translated.substring(lastIdx, matcher.start())));
      }
      lastIdx = matcher.end();

      final String argIdx = matcher.group(1);
      if (argIdx != null) {
        try {
          final int idx = Integer.parseInt(argIdx) - 1;
          if (idx < args.size()) {
            builder.append(args.get(idx).asComponent());
          }
        } catch (final NumberFormatException ignored) {
          // drop invalid placeholder
        }
      } else {
        final int idx = argPosition++;
        if (idx < args.size()) {
          builder.append(args.get(idx).asComponent());
        }
      }
    }

    if (lastIdx < translated.length()) {
      builder.append(Component.text(translated.substring(lastIdx)));
    }

    return this.optionallyRenderChildrenAppendAndBuild(component.children(), builder, context);
  }

  private Component optionallyRenderChildrenAndStyle(Component component, final Locale context) {
    final HoverEvent<?> hoverEvent = component.hoverEvent();
    if (hoverEvent != null) {
      component = component.hoverEvent(hoverEvent.withRenderedValue(this, context));
    }

    final List<Component> children = component.children();
    if (children.isEmpty()) return component;

    final List<Component> rendered = new ArrayList<>(children.size());
    children.forEach(child -> rendered.add(this.render(child, context)));

    return component.children(rendered);
  }

  private <O extends BuildableComponent<O, B>, B extends ComponentBuilder<O, B>> O mergeStyleAndOptionallyDeepRender(final Component component, final B builder, final Locale context) {
    this.mergeStyle(component, builder, context);
    return this.optionallyRenderChildrenAppendAndBuild(component.children(), builder, context);
  }

  private <O extends BuildableComponent<O, B>, B extends ComponentBuilder<O, B>> O optionallyRenderChildrenAppendAndBuild(final List<Component> children, final B builder, final Locale context) {
    if (!children.isEmpty()) {
      children.forEach(child -> builder.append(this.render(child, context)));
    }
    return builder.build();
  }

  private <B extends ComponentBuilder<?, ?>> void mergeStyle(final Component component, final B builder, final Locale context) {
    builder.mergeStyle(component, MERGES);
    builder.clickEvent(component.clickEvent());
    final HoverEvent<?> hoverEvent = component.hoverEvent();
    if (hoverEvent != null) {
      builder.hoverEvent(hoverEvent.withRenderedValue(this, context));
    }
  }

  private <O extends NBTComponent<O, B>, B extends NBTComponentBuilder<O, B>> B nbt(final Locale context, final B builder, final O oldComponent) {
    builder
      .nbtPath(oldComponent.nbtPath())
      .interpret(oldComponent.interpret());
    final Component separator = oldComponent.separator();
    if (separator != null) {
      builder.separator(this.render(separator, context));
    }
    return builder;
  }
}
