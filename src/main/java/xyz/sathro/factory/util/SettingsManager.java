package xyz.sathro.factory.util;

import org.apache.logging.log4j.core.Filter;
import xyz.sathro.factory.logger.MarkerConfig;

import java.util.function.Function;

public class SettingsManager {
	public static final MarkerConfig[] markers = new MarkerConfig[] {
			new MarkerConfig("UI", Filter.Result.DENY, Filter.Result.DENY),
			new MarkerConfig("Registries", Filter.Result.DENY, Filter.Result.DENY)
	};

	public static boolean logToFile = false;

	public static Setting<Boolean> fullScreen = new Setting<>("xyz.sathro.factory.fullScreen", SettingType.BOOLEAN);

	public static class Setting<T> {
		private T value;

		public Setting(T value) {
			this.value = value;
		}

		public Setting(String propertyName, SettingType<T> init) {
			this.value = init.apply(System.getProperty(propertyName));
		}

		public T get() {
			return value;
		}

		public void set(T newValue) {
			value = newValue;
		}
	}

	public interface SettingType<T> extends Function<String, T> {
		SettingType<Boolean> BOOLEAN = Boolean::parseBoolean;
		SettingType<Integer> INT = Integer::getInteger;
		SettingType<String> STRING = System::getProperty;
	}
}
