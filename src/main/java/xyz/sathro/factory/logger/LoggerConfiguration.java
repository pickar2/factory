package xyz.sathro.factory.logger;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.LayoutComponentBuilder;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import xyz.sathro.factory.util.SettingsManager;

import java.net.URI;

public class LoggerConfiguration extends ConfigurationFactory {
	static Configuration createConfiguration(final String name, ConfigurationBuilder<BuiltConfiguration> builder) {
		builder.setConfigurationName(name);
		builder.setStatusLevel(Level.ERROR);
		builder.add(builder.newFilter("ThresholdFilter", Filter.Result.ACCEPT, Filter.Result.NEUTRAL).
				addAttribute("level", Level.DEBUG));

		ConsoleBuilderInit(builder);
		if (SettingsManager.logToFile) {
			FileBuilderInit(builder);
		}

		builder.add(builder.newLogger("org.apache.logging.log4j", Level.DEBUG).
				add(builder.newAppenderRef("Stdout")).
				addAttribute("additivity", false));

		builder.add(builder.newRootLogger(Level.ERROR).add(builder.newAppenderRef("Stdout")));
		return builder.build();
	}

	private static void ConsoleBuilderInit(ConfigurationBuilder<BuiltConfiguration> builder) {
		final AppenderComponentBuilder consoleBuilder = builder.newAppender("Stdout", "CONSOLE").addAttribute("target",
		                                                                                                      ConsoleAppender.Target.SYSTEM_OUT);
		consoleBuilder.add(builder.newLayout("PatternLayout").
				addAttribute("pattern", "[%d{HH:mm:ss.SSS}|%level] %msg%n"));
		final LayoutComponentBuilder list = builder.newLayout("Filters");
		for (MarkerConfig marker : SettingsManager.markers) {
			list.addComponent(builder.newFilter("MarkerFilter", marker.consoleShow,
			                                    Filter.Result.NEUTRAL).addAttribute("marker", marker.markerName));
		}
		consoleBuilder.add(list);
		builder.add(consoleBuilder);
	}

	private static void FileBuilderInit(ConfigurationBuilder<BuiltConfiguration> builder) {
		final AppenderComponentBuilder fileBuilder = builder.newAppender("log", "File").addAttribute("fileName", "logging.log");
		fileBuilder.add(builder.newLayout("PatternLayout").
				addAttribute("pattern", "[%d{HH:mm:ss.SSS}|%level] %msg%n"));
		final LayoutComponentBuilder list = builder.newLayout("Filters");
		for (MarkerConfig marker : SettingsManager.markers) {
			list.addComponent(builder.newFilter("MarkerFilter", marker.fileShow,
			                                    Filter.Result.NEUTRAL).addAttribute("marker", marker.markerName));
		}
		fileBuilder.add(list);
		builder.add(fileBuilder);
	}

	@Override
	public Configuration getConfiguration(final LoggerContext loggerContext, final ConfigurationSource source) {
		return getConfiguration(loggerContext, source.toString(), null);
	}

	@Override
	public Configuration getConfiguration(final LoggerContext loggerContext, final String name, final URI configLocation) {
		ConfigurationBuilder<BuiltConfiguration> builder = newConfigurationBuilder();
		return createConfiguration(name, builder);
	}

	@Override
	protected String[] getSupportedTypes() {
		return new String[] { "*" };
	}

//	-Dlog4j2.configurationFactory=com.baeldung.log4j2.CustomConfigFactory
//	@Plugin(
//			name = "CustomConfigurationFactory",
//			category = ConfigurationFactory.CATEGORY)
//	@Order(50)
//	public class CustomConfigFactory
//			extends ConfigurationFactory {
//
//		// ... rest of implementation
//	}
}
