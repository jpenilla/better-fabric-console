package xyz.jpenilla.endermux.log4j;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.ansi.ColorLevel;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;
import org.apache.logging.log4j.core.pattern.PatternConverter;
import org.apache.logging.log4j.core.pattern.PatternFormatter;
import org.apache.logging.log4j.core.pattern.PatternParser;
import org.apache.logging.log4j.util.PerformanceSensitive;
import org.apache.logging.log4j.util.PropertiesUtil;

/**
 * Modified version of Paper's HexFormattingConverter to work with Kyori '§#rrggbb' format
 *
 * <p>"Deprecated", but kept around for some mods and datapacks that still log legacy codes.</p>
 */
@Plugin(name = "legacyMinecraftFormatting", category = PatternConverter.CATEGORY)
@ConverterKeys({"legacyMinecraftFormatting", "paperMinecraftFormatting"})
@PerformanceSensitive("allocation")
public final class HexFormattingConverter extends LogEventPatternConverter {

    public static final String KEEP_FORMATTING_PROPERTY = "terminal.keepMinecraftFormatting";
    private static final boolean KEEP_FORMATTING = PropertiesUtil.getProperties().getBooleanProperty(KEEP_FORMATTING_PROPERTY);

    private static final String ANSI_RESET = "\u001B[m";

    private static final char COLOR_CHAR = '§';
    private static final String LOOKUP = "0123456789abcdefklmnor";

    private static final String RGB_ANSI = "\u001B[38;2;%d;%d;%dm";
    private static final String RESET_RGB_ANSI = ANSI_RESET + RGB_ANSI;
    private static final Pattern NAMED_PATTERN = Pattern.compile(COLOR_CHAR + "[0-9a-fk-orA-FK-OR]");
    private static final Pattern RGB_PATTERN = Pattern.compile(COLOR_CHAR + "#([0-9a-fA-F]){6}");

    private static final String[] RGB_ANSI_CODES = new String[]{
            formatHexAnsi(NamedTextColor.BLACK.value()),         // Black §0
            formatHexAnsi(NamedTextColor.DARK_BLUE.value()),     // Dark Blue §1
            formatHexAnsi(NamedTextColor.DARK_GREEN.value()),    // Dark Green §2
            formatHexAnsi(NamedTextColor.DARK_AQUA.value()),     // Dark Aqua §3
            formatHexAnsi(NamedTextColor.DARK_RED.value()),      // Dark Red §4
            formatHexAnsi(NamedTextColor.DARK_PURPLE.value()),   // Dark Purple §5
            formatHexAnsi(NamedTextColor.GOLD.value()),          // Gold §6
            formatHexAnsi(NamedTextColor.GRAY.value()),          // Gray §7
            formatHexAnsi(NamedTextColor.DARK_GRAY.value()),     // Dark Gray §8
            formatHexAnsi(NamedTextColor.BLUE.value()),          // Blue §9
            formatHexAnsi(NamedTextColor.GREEN.value()),         // Green §a
            formatHexAnsi(NamedTextColor.AQUA.value()),          // Aqua §b
            formatHexAnsi(NamedTextColor.RED.value()),           // Red §c
            formatHexAnsi(NamedTextColor.LIGHT_PURPLE.value()),  // Light Purple §d
            formatHexAnsi(NamedTextColor.YELLOW.value()),        // Yellow §e
            formatHexAnsi(NamedTextColor.WHITE.value()),         // White §f
            "\u001B[5m",                                         // Obfuscated §k
            "\u001B[1m",                                         // Bold §l
            "\u001B[9m",                                         // Strikethrough §m
            "\u001B[4m",                                         // Underline §n
            "\u001B[3m",                                         // Italic §o
            ANSI_RESET,                                          // Reset §r
    };
    private static final String[] ANSI_ANSI_CODES = new String[]{
            ANSI_RESET + "\u001B[0;30m",    // Black §0
            ANSI_RESET + "\u001B[0;34m",    // Dark Blue §1
            ANSI_RESET + "\u001B[0;32m",    // Dark Green §2
            ANSI_RESET + "\u001B[0;36m",    // Dark Aqua §3
            ANSI_RESET + "\u001B[0;31m",    // Dark Red §4
            ANSI_RESET + "\u001B[0;35m",    // Dark Purple §5
            ANSI_RESET + "\u001B[0;33m",    // Gold §6
            ANSI_RESET + "\u001B[0;37m",    // Gray §7
            ANSI_RESET + "\u001B[0;30;1m",  // Dark Gray §8
            ANSI_RESET + "\u001B[0;34;1m",  // Blue §9
            ANSI_RESET + "\u001B[0;32;1m",  // Green §a
            ANSI_RESET + "\u001B[0;36;1m",  // Aqua §b
            ANSI_RESET + "\u001B[0;31;1m",  // Red §c
            ANSI_RESET + "\u001B[0;35;1m",  // Light Purple §d
            ANSI_RESET + "\u001B[0;33;1m",  // Yellow §e
            ANSI_RESET + "\u001B[0;37;1m",  // White §f
            "\u001B[5m",                    // Obfuscated §k
            "\u001B[1m",                    // Bold §l
            "\u001B[9m",                    // Strikethrough §m
            "\u001B[4m",                    // Underline §n
            "\u001B[3m",                    // Italic §o
            ANSI_RESET,                     // Reset §r
    };

    private final boolean ansi;
    private final List<PatternFormatter> formatters;

    /**
     * Construct the converter.
     *
     * @param formatters The pattern formatters to generate the text to manipulate
     * @param strip      If true, the converter will strip all formatting codes
     */
    protected HexFormattingConverter(List<PatternFormatter> formatters, boolean strip) {
        super("legacyMinecraftFormatting", null);
        this.formatters = formatters;
        this.ansi = !strip;
    }

    @Override
    public void format(LogEvent event, StringBuilder toAppendTo) {
        int start = toAppendTo.length();
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0, size = formatters.size(); i < size; i++) {
            formatters.get(i).format(event, toAppendTo);
        }

        if (KEEP_FORMATTING || toAppendTo.length() == start) {
            // Skip replacement if disabled or if the content is empty
            return;
        }

        boolean useAnsi = ansi && ColorLevel.compute() != ColorLevel.NONE;
        String content = toAppendTo.substring(start);
        content = useAnsi ? convertRGBColors(content) : stripRGBColors(content);
        format(content, toAppendTo, start, useAnsi);
    }

    private static String convertRGBColors(final String input) {
        return RGB_PATTERN.matcher(input).replaceAll(result -> {
            final int hex = Integer.decode(result.group().substring(1));
            return formatHexAnsi(hex);
        });
    }

    private static String formatHexAnsi(final int color) {
        final int red = color >> 16 & 0xFF;
        final int green = color >> 8 & 0xFF;
        final int blue = color & 0xFF;
        return String.format(RESET_RGB_ANSI, red, green, blue);
    }

    private static String stripRGBColors(final String input) {
        return RGB_PATTERN.matcher(input).replaceAll("");
    }

    static void format(String content, StringBuilder result, int start, boolean ansi) {
        int next = content.indexOf(COLOR_CHAR);
        int last = content.length() - 1;
        if (next == -1 || next == last) {
            result.setLength(start);
            result.append(content);
            if (ansi) {
                result.append(ANSI_RESET);
            }
            return;
        }

        Matcher matcher = NAMED_PATTERN.matcher(content);
        StringBuilder buffer = new StringBuilder();
        final String[] ansiCodes = ansiCodes();
        while (matcher.find()) {
            int format = LOOKUP.indexOf(Character.toLowerCase(matcher.group().charAt(1)));
            if (format != -1) {
                matcher.appendReplacement(buffer, ansi ? ansiCodes[format] : "");
            }
        }
        matcher.appendTail(buffer);

        result.setLength(start);
        result.append(buffer);
        if (ansi) {
            result.append(ANSI_RESET);
        }
    }

    private static String[] ansiCodes() {
        final boolean rgb = ColorLevel.compute() == ColorLevel.TRUE_COLOR;
        return rgb ? RGB_ANSI_CODES : ANSI_ANSI_CODES;
    }

    /**
     * Gets a new instance of the {@link HexFormattingConverter} with the
     * specified options.
     *
     * @param config  The current configuration
     * @param options The pattern options
     * @return The new instance
     * @see HexFormattingConverter
     */
    public static HexFormattingConverter newInstance(Configuration config, String[] options) {
        if (options.length < 1 || options.length > 2) {
            LOGGER.error("Incorrect number of options on legacyMinecraftFormatting. Expected at least 1, max 2 received " + options.length);
            return null;
        }
        if (options[0] == null) {
            LOGGER.error("No pattern supplied on legacyMinecraftFormatting");
            return null;
        }

        PatternParser parser = PatternLayout.createPatternParser(config);
        List<PatternFormatter> formatters = parser.parse(options[0]);
        boolean strip = options.length > 1 && "strip".equals(options[1]);
        return new HexFormattingConverter(formatters, strip);
    }

}
