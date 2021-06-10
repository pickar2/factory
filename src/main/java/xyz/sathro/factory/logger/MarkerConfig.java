package xyz.sathro.factory.logger;

import org.apache.logging.log4j.core.Filter;

public class MarkerConfig {
	public final String markerName;
	public final Filter.Result consoleShow;
	public final Filter.Result fileShow;

	public MarkerConfig(String markerName, Filter.Result consoleShow, Filter.Result fileShow) {
		this.markerName = markerName;
		this.consoleShow = consoleShow;
		this.fileShow = fileShow;
	}
}
