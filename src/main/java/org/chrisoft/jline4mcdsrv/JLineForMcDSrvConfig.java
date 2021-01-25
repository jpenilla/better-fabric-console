package org.chrisoft.jline4mcdsrv;

import me.sargunvohra.mcmods.autoconfig1u.ConfigData;
import me.sargunvohra.mcmods.autoconfig1u.annotation.Config;

import java.util.*;

@Config(name = "jline4mcdsrv")
public class JLineForMcDSrvConfig implements ConfigData
{
	// Represent AttributedStyle.BLACK = 0, AttributedStyle.RED = 1, ... AttributedStyle.WHITE = 7
	private enum StyleColor {
		BLACK, RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN, WHITE
	}

	public String logPattern = "%style{[%d{HH:mm:ss}]}{blue} "
		+ "%highlight{[%t/%level]}{FATAL=red, ERROR=red, WARN=yellow, INFO=green, DEBUG=green, TRACE=blue} "
		+ "%style{(%logger{1})}{cyan} "
		+ "%highlight{%msg%n}{FATAL=red, ERROR=red, WARN=normal, INFO=normal, DEBUG=normal, TRACE=normal}";

	private StyleColor[] highlightColors = {StyleColor.CYAN, StyleColor.YELLOW, StyleColor.GREEN, StyleColor.MAGENTA, StyleColor.WHITE};

	public transient int[] highlightColorIndices;

	@Override
	public void validatePostLoad() throws ConfigData.ValidationException {
		// transform the color names into their AttributedStyle index
		highlightColorIndices = new int[highlightColors.length];
		for (int i = 0; i < highlightColors.length; i++) {
			if (highlightColors[i] == null)
				throw new ConfigData.ValidationException("highlightColors[" + i + "] needs to be one of " + Arrays.toString(StyleColor.values()));

			highlightColorIndices[i] = highlightColors[i].ordinal();
		}
	}
}
