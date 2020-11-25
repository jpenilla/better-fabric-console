package org.chrisoft.jline4mcdsrv;

import me.sargunvohra.mcmods.autoconfig1u.ConfigData;
import me.sargunvohra.mcmods.autoconfig1u.annotation.Config;

import java.util.*;

@Config(name = "jline4mcdsrv")
public class JLineForMcDSrvConfig implements ConfigData
{
	//Represent AttributedStyle.BLACK = 0, AttributedStyle.RED = 1 etc
	public static final List<String> colorList = Collections.unmodifiableList(new ArrayList<>(Arrays.asList(
		"BLACK", "RED", "GREEN", "YELLOW", "BLUE", "MAGENTA", "CYAN", "WHITE")));

	public String logPattern = "%style{[%d{HH:mm:ss}]}{blue} "
		+ "%highlight{[%t/%level]}{FATAL=red, ERROR=red, WARN=yellow, INFO=green, DEBUG=green, TRACE=blue} "
		+ "%style{(%logger{1})}{cyan} "
		+ "%highlight{%msg%n}{FATAL=red, ERROR=red, WARN=normal, INFO=normal, DEBUG=normal, TRACE=normal}";

	public String[] highlightColors = {"CYAN", "YELLOW", "GREEN", "MAGENTA", "WHITE"};

	@Override
	public void validatePostLoad() throws ConfigData.ValidationException {
		for (int i = 0; i < highlightColors.length; i++) {
			highlightColors[i] = highlightColors[i].toUpperCase();

			int styleColor = colorList.indexOf(highlightColors[i]);
			if (styleColor == -1)
				throw new ConfigData.ValidationException("highlightColors[" + i + "] needs to be one of " + colorList);
		}
	}

	//for use in Highlighter
	public int[] getHighlightColors() {
		int[] styleColors = new int[highlightColors.length];
		for (int i = 0; i < highlightColors.length; i++) {
			int styleColor = colorList.indexOf(highlightColors[i]);
			styleColors[i] = styleColor;
		}
		return styleColors;
	}
}
