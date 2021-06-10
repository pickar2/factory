package xyz.sathro.factory.util;

import org.apache.logging.log4j.core.Filter;
import xyz.sathro.factory.logger.MarkerConfig;

public class SettingsManager {
	// Logger
	public static boolean logToFile = false;
	public static final MarkerConfig[] markers = new MarkerConfig[] {
			new MarkerConfig("UI", Filter.Result.DENY, Filter.Result.DENY),
			new MarkerConfig("Registries", Filter.Result.DENY, Filter.Result.DENY)
	};

	public static final boolean fullScreen = false;
}
