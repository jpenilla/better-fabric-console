package org.chrisoft.jline4mcdsrv;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

@ConfigSerializable
final class Config {
    /**
     * Mirrors {@link org.jline.utils.AttributedStyle} color constants.
     */
    enum StyleColor {
        BLACK(0),
        RED(1),
        GREEN(2),
        YELLOW(3),
        BLUE(4),
        MAGENTA(5),
        CYAN(6),
        WHITE(7);

        private final int index;

        StyleColor(final int index) {
            this.index = index;
        }

        public int index() {
            return this.index;
        }
    }

    @Comment("Log4j logger pattern")
    private String logPattern = "%highlight{[%d{HH:mm:ss} %level] [%t]: [%logger{1}]}{FATAL=red, ERROR=red, WARN=yellow, INFO=default, DEBUG=yellow, TRACE=blue} %paperMinecraftFormatting{%msg}%n";

    public @NonNull String logPattern() {
        return this.logPattern;
    }

    @Comment("Specify argument highlight colors, in order. Possible values: [BLACK, RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN, WHITE]")
    private StyleColor[] highlightColors = {StyleColor.CYAN, StyleColor.YELLOW, StyleColor.GREEN, StyleColor.MAGENTA, StyleColor.WHITE};

    public @NonNull StyleColor @NonNull [] highlightColors() {
        return this.highlightColors;
    }
}
