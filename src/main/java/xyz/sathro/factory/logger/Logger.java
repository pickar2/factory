package xyz.sathro.factory.logger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.ConfigurationFactory;

public class Logger {
	static {
		ConfigurationFactory custom = new LoggerConfiguration();
		ConfigurationFactory.setConfigurationFactory(custom);
	}

	public static final org.apache.logging.log4j.Logger instance = LogManager.getLogger(Logger.class);
}
