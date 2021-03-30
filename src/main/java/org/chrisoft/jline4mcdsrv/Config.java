package org.chrisoft.jline4mcdsrv;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

@ConfigSerializable
final class Config {
    // Represent AttributedStyle.BLACK = 0, AttributedStyle.RED = 1, ... AttributedStyle.WHITE = 7
    private enum StyleColor {
        BLACK, RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN, WHITE
    }

    @Comment("Log4j logger pattern")
    private String logPattern = "%highlight{[%d{HH:mm:ss} %level] [%t]: [%logger{1}]}{FATAL=red, ERROR=red, WARN=yellow, INFO=default, DEBUG=yellow, TRACE=blue} %paperMinecraftFormatting{%msg}%n";

    public @NonNull String logPattern() {
        return this.logPattern;
    }

    @Comment("Specify argument highlight colors, in order. Possible values: [BLACK, RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN, WHITE]")
    private StyleColor[] highlightColors = {StyleColor.CYAN, StyleColor.YELLOW, StyleColor.GREEN, StyleColor.MAGENTA, StyleColor.WHITE};

    private transient int[] highlightColorIndices;

    public int @NonNull [] highlightColorIndices() {
        return this.highlightColorIndices;
    }

    public void populateIndices() {
        // transform the color names into their AttributedStyle index
        this.highlightColorIndices = new int[highlightColors.length];
        for (int i = 0; i < this.highlightColors.length; i++) {
            this.highlightColorIndices[i] = this.highlightColors[i].ordinal();
        }
    }
}
